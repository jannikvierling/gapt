package at.logic.gapt.formats.babel

import at.logic.gapt.expr._
import org.kiama.output.PrettyPrinter

class BabelExporter( unicode: Boolean ) extends PrettyPrinter {

  def export( expr: LambdaExpression ): String =
    pretty( group( show( expr, false, Map(), prio.max )._1 ) )

  object prio {
    val ident = 0
    val app = 2
    val timesDiv = 4
    val plusMinus = 6
    val infixRel = 8
    val quantOrNeg = 10
    val conj = 12
    val disj = 16
    val bicond = 18
    val impl = 20
    val ascript = 22
    val lam = 24

    val max = lam + 1
  }

  val infixRel = Set( "<", "<=", ">", ">=" )

  def show(
    expr:      LambdaExpression,
    knownType: Boolean,
    t0:        Map[String, LambdaExpression],
    p:         Int
  ): ( Doc, Map[String, LambdaExpression] ) =
    expr match {
      case Top()    => ( value( if ( unicode ) "⊤" else "true" ), t0 )
      case Bottom() => ( value( if ( unicode ) "⊥" else "false" ), t0 )

      case Apps( Const( rel, _ ), Seq( a, b ) ) if infixRel( rel ) =>
        showBin( rel, prio.infixRel, 0, 0, a, b, false, t0, p )
      case Apps( Const( "+", _ ), Seq( a, b ) ) =>
        showBin( "+", prio.plusMinus, 1, 1, a, b, false, t0, p )
      case Apps( Const( "-", _ ), Seq( a, b ) ) =>
        showBin( "-", prio.plusMinus, 1, 0, a, b, false, t0, p )
      case Apps( Const( "*", _ ), Seq( a, b ) ) =>
        showBin( "*", prio.timesDiv, 1, 1, a, b, false, t0, p )
      case Apps( Const( "/", _ ), Seq( a, b ) ) =>
        showBin( "/", prio.timesDiv, 1, 0, a, b, false, t0, p )

      case Eq( a, b ) =>
        val ( a_, t1 ) = show( a, false, t0, prio.infixRel )
        val ( b_, t2 ) = show( b, true, t1, prio.infixRel )
        ( parenIf( p, prio.infixRel, a_ <+> "=" <@> b_ ), t2 )

      case Neg( e ) =>
        val ( e_, t1 ) = show( e, true, t0, prio.quantOrNeg + 1 )
        ( parenIf( p, prio.quantOrNeg, ( if ( unicode ) "¬" else "-" ) <> e_ ), t1 )

      case And( a, b ) => showBin( if ( unicode ) "∧" else "&", prio.conj, 1, 0, a, b, true, t0, p )
      case Or( a, b )  => showBin( if ( unicode ) "∨" else "|", prio.disj, 1, 0, a, b, true, t0, p )
      case Imp( a, b ) => showBin( if ( unicode ) "⊃" else "->", prio.impl, 0, 1, a, b, true, t0, p )

      case Abs( v @ Var( vn, vt ), e ) =>
        val v_ =
          if ( knownType || vt == Ti )
            showName( vn )
          else
            parens( showName( vn ) <> ":" <> show( vt, false ) )
        val t1 = t0 + ( vn -> v )
        val ( e_, t2 ) = show( e, knownType, t1, prio.lam + 1 )
        ( parenIf( p, prio.lam, ( if ( unicode ) "λ" else "^" ) <> v_ <@> e_ ),
          if ( t0 contains vn ) t2 + ( vn -> t0( vn ) )
          else t2 - vn )

      case All( v, e ) => showQuant( if ( unicode ) "∀" else "!", v, e, t0, p )
      case Ex( v, e )  => showQuant( if ( unicode ) "∃" else "?", v, e, t0, p )

      case Apps( hd, args ) if args.nonEmpty =>
        val hdSym = hd match {
          case Const( n, _ ) => Some( n )
          case Var( n, _ )   => Some( n )
          case _             => None
        }
        var t1 = t0 ++ hdSym.map { _ -> hd }
        if ( knownType || expr.exptype == Ti || hdSym.exists { n => t0 get n contains hd } ) {
          val args_ = for ( arg <- args ) yield {
            val ( arg_, t10 ) = show( arg, false, t1, prio.max )
            t1 = t10
            arg_
          }
          val ( hd_, t2 ) = show( hd, true, t1, prio.app )
          ( parenIf( p, prio.app, hd_ ) <> parens( fillsep( args_, comma ) ), t2 )
        } else {
          val ( expr_, t2 ) = show( expr, true, t1, prio.ascript )
          ( parenIf( p, prio.ascript, expr_ <+> ":" <@> show( expr.exptype, false ) ), t2 )
        }

      case Const( name, ty ) =>
        if ( ty == Ti || knownType || t0.get( name ).contains( expr ) )
          ( showName( name ), t0 + ( name -> expr ) )
        else
          ( parenIf( p, prio.ascript, showName( name ) <> ":" <> show( ty, false ) ), t0 + ( name -> expr ) )
      case Var( name, ty ) =>
        if ( ty == Ti || knownType || t0.get( name ).contains( expr ) )
          ( showName( name ), t0 + ( name -> expr ) )
        else
          ( parenIf( p, prio.ascript, showName( name ) <> ":" <> show( ty, false ) ), t0 + ( name -> expr ) )
    }

  def showBin(
    sym:           String,
    prio:          Int,
    leftPrioBias:  Int,
    rightPrioBias: Int,
    a:             LambdaExpression,
    b:             LambdaExpression,
    knownType:     Boolean,
    t0:            Map[String, LambdaExpression],
    p:             Int
  ): ( Doc, Map[String, LambdaExpression] ) = {
    val ( a_, t1 ) = show( a, knownType, t0, prio + leftPrioBias )
    val ( b_, t2 ) = show( b, knownType, t1, prio + rightPrioBias )
    ( parenIf( p, prio, a_ <+> sym <@> b_ ), t2 )
  }

  def showQuant(
    sym: String,
    v:   Var,
    e:   LambdaExpression,
    t0:  Map[String, LambdaExpression],
    p:   Int
  ): ( Doc, Map[String, LambdaExpression] ) = {
    val Var( vn, vt ) = v
    val v_ =
      if ( vt == Ti )
        showName( vn )
      else
        parens( showName( vn ) <> ":" <> show( vt, false ) )
    val t1 = t0 + ( vn -> v )
    val ( e_, t2 ) = show( e, true, t1, prio.quantOrNeg + 1 )
    ( parenIf( p, prio.quantOrNeg, sym <> v_ <@> e_ ),
      if ( t0 contains vn ) t2 + ( vn -> t0( vn ) )
      else t2 - vn )
  }

  val unquotedName = """[A-Za-z0-9_$]+""".r
  val safeChars = """[A-Za-z0-9 ~!@#$%^&*()_=+{}|;:,./<>?-]|\[|\]""".r
  def showName( name: String ): Doc = name match {
    case "true"         => "'true'"
    case "false"        => "'false'"
    case unquotedName() => name
    case _ => "'" + name.map {
      case c @ safeChars() =>
        c
      case '''          => "\\'"
      case '\\'         => "\\\\"
      case c if unicode => c
      case c            => "\\u%04x".format( c.toChar.toInt )
    }.mkString + "'"
  }

  def show( ty: Ty, needParens: Boolean ): Doc = ty match {
    case TBase( name ) => showName( name )
    case a -> b if !needParens =>
      group( show( a, true ) <> ">" <@@> show( b, false ) )
    case _ => parens( nest( show( ty, false ) ) )
  }

  def parenIf( enclosingPrio: Int, currentPrio: Int, doc: Doc ) =
    if ( enclosingPrio <= currentPrio )
      parens( group( nest( doc ) ) )
    else
      group( doc )
}
