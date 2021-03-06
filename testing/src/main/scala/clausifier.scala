package at.logic.gapt.testing

import at.logic.gapt.formats.{ InputFile, StdinInputFile }
import at.logic.gapt.formats.tptp._
import ammonite.ops._
import at.logic.gapt.proofs.resolution.structuralCNF

object clausifier extends App {
  val input: InputFile = args match {
    case Array( fn ) => FilePath( fn )
    case _           => StdinInputFile()
  }
  val tptp = TptpParser.load( input )
  val inputClauses = tptpProblemToResolution( tptp )
  val cnf = structuralCNF.onProofs( inputClauses )
  val tptpCNF = TptpFile( for ( ( cls, i ) <- cnf.toSeq.zipWithIndex )
    yield resolutionToTptp.fofOrCnf( s"cls_$i", "axiom", cls, Seq() ) )
  println( tptpCNF )
}
