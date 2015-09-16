package at.logic.gapt.testing

import java.util.concurrent.TimeoutException

import at.logic.gapt.expr.{ EqC, constants }
import at.logic.gapt.formats.leanCoP.LeanCoPParser
import java.io._
import java.nio.file.{ Paths, Files }
import at.logic.gapt.examples._
import at.logic.gapt.formats.veriT.VeriTParser
import at.logic.gapt.proofs.lk.base.LKRuleCreationException
import at.logic.gapt.proofs.lk.{ LKToExpansionProof, rulesNumber, containsEqualityReasoning }
import at.logic.gapt.proofs.lk.cutIntroduction._
import at.logic.gapt.proofs.resolution.{ numberOfResolutionsAndParamodulations, RobinsonToExpansionProof }
import at.logic.gapt.provers.maxsat.OpenWBO
import at.logic.gapt.provers.prover9.Prover9Importer
import at.logic.gapt.utils.logging.{ metrics, CollectMetrics }

import scala.collection.immutable.HashMap

import at.logic.gapt.utils.executionModels.timeout._
import at.logic.gapt.proofs.expansionTrees.{ addSymmetry, toShallow }

import org.json4s._
import org.json4s.native.JsonMethods._

import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Random

object testCutIntro extends App {

  lazy val proofSeqRegex = """\w+\((\d+)\)""".r
  def loadProof( fileName: String ) = fileName match {
    case proofSeqRegex( name, n ) =>
      val p = proofSequences.find( _.name == name ).get( n.toInt )
      val hasEquality = containsEqualityReasoning( p )
      metrics.value( "lkinf_input", rulesNumber( p ) )
      Some( LKToExpansionProof( p ) -> hasEquality )
    case _ if fileName endsWith ".proof_flat" =>
      VeriTParser.getExpansionProof( fileName ) map { ep => addSymmetry( ep ) -> false }
    case _ if fileName contains "/leanCoP/" =>
      LeanCoPParser.getExpansionProof( fileName ) map { _ -> true }
    case _ if fileName contains "/prover9/" =>
      val ( resProof, endSequent ) = Prover9Importer.robinsonProofWithReconstructedEndSequentFromFile( fileName )
      metrics.value( "resinf_input", numberOfResolutionsAndParamodulations( resProof ) )
      val expansionProof = RobinsonToExpansionProof( resProof, endSequent )
      val containsEquations = constants( toShallow( expansionProof ) ) exists {
        case EqC( _ ) => true
        case _        => false
      }
      Some( expansionProof -> containsEquations )
  }

  def getMethod( methodName: String ) = methodName match {
    case "1_dtable"    => DeltaTableMethod( manyQuantifiers = false )
    case "many_dtable" => DeltaTableMethod( manyQuantifiers = true )
    case _ if methodName endsWith "_maxsat" =>
      val vectorSizes = methodName.dropRight( "_maxsat".length ).split( "_" ).map( _.toInt )
      MaxSATMethod( new OpenWBO, vectorSizes: _* )
  }

  lazy val proofs =
    ( for ( n <- 0 to 100; proofSequence <- proofSequences ) yield s"${proofSequence.name}($n)" ) ++
      recursiveListFiles( "testing/TSTP/prover9" ).map( _.getPath ).filter( _ endsWith ".s.out" ) ++
      recursiveListFiles( "testing/veriT-SMT-LIB-QF_UF" ).map( _.getPath ).filter( _ endsWith ".proof_flat" ) ++
      recursiveListFiles( "testing/TSTP/leanCoP" ).map( _.getPath ).filter( _ endsWith ".s.out" ) ++
      Nil

  lazy val methods = Seq( "1_dtable", "many_dtable", "1_maxsat", "1_1_maxsat", "2_maxsat", "2_2_maxsat" )

  lazy val experiments = for ( p <- proofs; m <- methods ) yield ( p, m )

  def timeout = 60 seconds

  def runExperiment( fileName: String, methodName: String ): JValue = {
    val collectedMetrics = new CollectMetrics
    val f = Future {
      metrics.current.withValue( collectedMetrics ) {
        metrics.value( "file", fileName )
        metrics.value( "method", methodName )
        val parseResult = try metrics.time( "parse" ) {
          loadProof( fileName ) orElse {
            metrics.value( "status", "parsing_proof_not_found" )
            None
          }
        } catch {
          case e: OutOfMemoryError =>
            metrics.value( "status", "parsing_out_of_memory" )
            None
          case e: StackOverflowError =>
            metrics.value( "status", "parsing_stack_overflow" )
            None
          case e: Throwable =>
            metrics.value( "status", "parsing_other_exception" )
            None
        }

        parseResult foreach {
          case ( expansionProof, hasEquality ) =>
            metrics.value( "has_equality", hasEquality )
            try metrics.time( "cutintro" ) {
              CutIntroduction.compressToLK( expansionProof, hasEquality, getMethod( methodName ), verbose = false ) match {
                case Some( _ ) => metrics.value( "status", "ok" )
                case None      => metrics.value( "status", "cutintro_uncompressible" )
              }
            }
            catch {
              case e: OutOfMemoryError =>
                metrics.value( "status", "cutintro_out_of_memory" )
              case e: StackOverflowError =>
                metrics.value( "status", "cutintro_stack_overflow" )
              case e: CutIntroEHSUnprovableException =>
                metrics.value( "status", "cutintro_ehs_unprovable" )
              case e: LKRuleCreationException =>
                metrics.value( "status", "lk_rule_creation_exception" )
              case e: Throwable =>
                metrics.value( "status", "cutintro_other_exception" )
            }
        }
      }
    }

    val results = try {
      Await.result( f, timeout )
      collectedMetrics.copy
    } catch {
      case _: TimeoutException =>
        val res = collectedMetrics.copy
        res.value( "status", if ( res.currentPhase == "parsing" ) "parsing_timeout" else "cutintro_timeout" )
        res
    }
    results.value( "phase", results.currentPhase )

    def jsonify( v: Any ): JValue = v match {
      case l: Long    => JInt( l )
      case l: Int     => JInt( l )
      case b: Boolean => JBool( b )
      case l: Seq[_]  => JArray( l map jsonify toList )
      case s          => JString( s toString )
    }

    JObject( results.data mapValues jsonify toList )
  }

  println( s"Running ${experiments.size} experiments." )

  val partialResultsOut = new PrintWriter( "partial_results.json" )
  var done = 0
  val experimentResults = Random.shuffle( experiments ).par flatMap {
    case ( p, m ) =>
      try {
        done += 1
        println( s"$done/${experiments.size}\t$p\t$m" )
        val beginTime = System.currentTimeMillis()
        val JObject( resultEntries ) =
          parse( runOutOfProcess[String]( Seq( "-Xmx1G", "-Xss30m" ) ) { compact( render( runExperiment( p, m ) ) ) } )
        val totalTime = System.currentTimeMillis() - beginTime
        val result = JObject( resultEntries :+ ( "time_total" -> JInt( totalTime ) ) )
        partialResultsOut.println( compact( render( result ) ) )
        partialResultsOut.flush()
        Some( result )
      } catch {
        case t: Throwable =>
          println( s"$p $m failed" )
          t.printStackTrace()
          None
      }
  }

  Files.write(
    Paths.get( "results.json" ),
    compact( render( JArray( experimentResults.toList ) ) ).getBytes
  )

  partialResultsOut.close()
}

object findNonTrivialTSTPExamples extends App {
  var total = 0
  var num_trivial_termset = 0
  var error_parser = 0
  var error_term_extraction = 0
  // Hashmap containing proofs with non-trivial termsets
  var termsets = HashMap[String, TermSet]()

  findNonTrivialTSTPExamples( "testing/TSTP/prover9", 60 )

  def findNonTrivialTSTPExamples( str: String, timeout: Int ) = {

    nonTrivialTermset( str, timeout )
    val file = new File( "testing/resultsCutIntro/tstp_non_trivial_termset.csv" )
    val summary = new File( "testing/resultsCutIntro/tstp_non_trivial_summary.txt" )
    file.createNewFile()
    summary.createNewFile()
    val fw = new FileWriter( file.getAbsoluteFile )
    val bw = new BufferedWriter( fw )
    val fw_s = new FileWriter( summary.getAbsoluteFile )
    val bw_s = new BufferedWriter( fw_s )

    val nLine = sys.props( "line.separator" )

    var instance_per_formula = 0.0
    var ts_size = 0
    val data = termsets.foldLeft( "" ) {
      case ( acc, ( k, v ) ) =>
        val tssize = v.set.size
        val n_functions = v.formulas.distinct.size
        instance_per_formula += tssize.toFloat / n_functions.toFloat
        ts_size += tssize
        k + "," + n_functions + "," + tssize + nLine + acc
    }

    val avg_inst_per_form = instance_per_formula / termsets.size
    val avg_ts_size = ts_size.toFloat / termsets.size.toFloat

    bw.write( data )
    bw.close()

    bw_s.write( "Total number of proofs: " + total + nLine )
    bw_s.write( "Total number of proofs with trivial termsets: " + num_trivial_termset + nLine )
    bw_s.write( "Total number of proofs with non-trivial termsets: " + termsets.size + nLine )
    bw_s.write( "Time limit exceeded or exception during parsing: " + error_parser + nLine )
    bw_s.write( "Time limit exceeded or exception during terms extraction: " + error_term_extraction + nLine )
    bw_s.write( "Average instances per quantified formula: " + avg_inst_per_form + nLine )
    bw_s.write( "Average termset size: " + avg_ts_size + nLine )
    bw_s.close()

  }
  def nonTrivialTermset( str: String, timeout: Int ): Unit = {
    val file = new File( str )
    if ( file.isDirectory ) {
      val children = file.listFiles
      children.foreach( f => nonTrivialTermset( f.getAbsolutePath, timeout ) )
    } else if ( file.getName.endsWith( ".out" ) ) {
      println( file )
      total += 1
      try {
        withTimeout( timeout seconds ) {
          val p = Prover9Importer.expansionProofFromFile( file.getAbsolutePath )
          try {
            val ts = TermsExtraction( p )
            val tssize = ts.set.size
            val n_functions = ts.formulas.distinct.size

            if ( tssize > n_functions )
              termsets += ( file.getAbsolutePath -> ts )
            else num_trivial_termset += 1
          } catch {
            case e: Throwable => error_term_extraction += 1
          }
        }
      } catch {
        case e: Throwable => error_parser += 1
      }
    }
  }

}
