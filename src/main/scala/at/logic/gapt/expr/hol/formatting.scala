package at.logic.gapt.expr.hol

import at.logic.gapt.expr._

/**
 * Created by marty on 4/16/15.
 */

/**
 * Formats a HOL expression without types and the outermost parenthesis. Conjunction, disjunction and implication
 * are considered right associative, i.e. a /\ ((b /\ c) /\ d) = a /\ (b /\ c) /\ d. Equation is rendered infix.
 */
object toPrettyString {
  def apply( e: LambdaExpression ) = string_of_expr( e, false )

  //TODO: introduce binding priorities of and over or.
  def string_of_expr( e: LambdaExpression, use_parens: Boolean = false ): String = e match {
    case Var( x, tpe )   => x.toString
    case Const( x, tpe ) => x.toString
    case Eq( left, right ) =>
      string_of_expr( left, true ) + " = " + string_of_expr( right, true )
    case HOLAtom( x, Nil ) => string_of_expr( x, false )
    case HOLAtom( x, args ) =>
      string_of_expr( x, false ) + args.map( x => string_of_expr( x, true ) ).mkString( "(", ", ", ")" )
    case HOLFunction( x, Nil ) => string_of_expr( x, false )
    case HOLFunction( x, args ) =>
      string_of_expr( x, false ) + args.map( x => string_of_expr( x, true ) ).mkString( "(", ", ", ")" )
    // right-assoc operators: and, or, imp
    case And( x, y @ And( _, _ ) ) => opt_parens( string_of_expr( x, true ) + AndC.name + string_of_expr( y, false ), use_parens )
    case Or( x, y @ Or( _, _ ) )   => opt_parens( string_of_expr( x, true ) + OrC.name + string_of_expr( y, true ), use_parens )
    case Imp( x, y @ Imp( _, _ ) ) => opt_parens( string_of_expr( x, true ) + ImpC.name + string_of_expr( y, true ), use_parens )
    // no parens for double negation
    case Neg( x @ Neg( _ ) )       => NegC.name + opt_parens( string_of_expr( x, false ), use_parens )
    // default case
    case And( x, y )               => opt_parens( string_of_expr( x, true ) + AndC.name + string_of_expr( y, true ), use_parens )
    case Eq( x, y )                => opt_parens( string_of_expr( x, true ) + EqC.name + string_of_expr( y, true ), use_parens )
    case Or( x, y )                => opt_parens( string_of_expr( x, true ) + OrC.name + string_of_expr( y, true ), use_parens )
    case Imp( x, y )               => opt_parens( string_of_expr( x, true ) + ImpC.name + string_of_expr( y, true ), use_parens )
    case Neg( x )                  => NegC.name + opt_parens( string_of_expr( x, true ), use_parens )
    case Ex( x, f )                => opt_parens( ExistsC.name + string_of_expr( x, false ) + "." + string_of_expr( f, true ), use_parens )
    case All( x, f )               => opt_parens( ForallC.name + string_of_expr( x, false ) + "." + string_of_expr( f, true ), use_parens )
    case Abs( v, exp )             => opt_parens( "λ" + string_of_expr( v ) + "." + string_of_expr( exp, true ), use_parens )
    case App( l, r )               => opt_parens( string_of_expr( l, true ) + " " + string_of_expr( r, true ), use_parens )
    case _                         => throw new Exception( "Unrecognized symbol." )
  }

  def opt_parens( str: String, use_parens: Boolean ) = if ( use_parens ) "(" + str + ")" else str
}

object toAbbreviatedString {
  /**
   * This function takes a HOL construction and converts it to a abbreviated string version. The abbreviated string version is made
   * by replacing the code construction for logic symbols by string versions in the file language/hol/logicC.names.scala.
   * Several recursive function calls will be transformed into an abbreviated form (e.g. f(f(f(x))) => f^3^(x)).
   * Terms are also handled by the this function.
   * @param  e  The method has no parameters other then the object which is to be written as a string
   * @throws Exception This occurs when an unknown subformula is found when parsing the HOL construction
   * @return A String which contains the defined symbols in language/hol/logicC.names.scala.
   *
   */
  def apply( e: LambdaExpression ): String = {

    val p = pretty( e )

    val r: String = e match {
      case HOLFunction( x, args ) => {
        if ( p._1 != p._2 && p._2 != "tuple1" )
          if ( p._3 > 0 )
            return p._2 + "^" + ( p._3 + 1 ) + "(" + p._1 + ") : " + e.exptype
          else
            return p._1
        else
          return p._1
      }
      case _ => return p._1
    }

    return r
  }

  private def pretty( exp: LambdaExpression ): ( String, String, Int ) = {

    val s: ( String, String, Int ) = exp match {
      case null                => ( "null", "null", -2 )
      case Var( x, t )         => ( x.toString() + " : " + t.toString(), x.toString(), 0 )
      case And( x, y )         => ( "(" + toAbbreviatedString( x ) + " " + AndC.name + " " + toAbbreviatedString( y ) + ")", AndC.name, 0 )
      case Eq( x, y )          => ( "(" + toAbbreviatedString( x ) + " " + EqC.name + " " + toAbbreviatedString( y ) + ")", EqC.name, 0 )
      case Or( x, y )          => ( "(" + toAbbreviatedString( x ) + " " + OrC.name + " " + toAbbreviatedString( y ) + ")", OrC.name, 0 )
      case Imp( x, y )         => ( "(" + toAbbreviatedString( x ) + " " + ImpC.name + " " + toAbbreviatedString( y ) + ")", ImpC.name, 0 )
      case Neg( x )            => ( NegC.name + toAbbreviatedString( x ), NegC.name, 0 )
      case Ex( x, f )          => ( ExistsC.name + toAbbreviatedString( x ) + "." + toAbbreviatedString( f ), ExistsC.name, 0 )
      case All( x, f )         => ( ForallC.name + toAbbreviatedString( x ) + "." + toAbbreviatedString( f ), ForallC.name, 0 )
      case Abs( v, exp )       => ( "(λ" + toAbbreviatedString( v ) + "." + toAbbreviatedString( exp ) + ")", "λ", 0 )
      case App( l, r )         => ( "(" + toAbbreviatedString( l ) + ")(" + toAbbreviatedString( r ) + ")", "()()", 0 )
      case Const( x, exptype ) => ( x.toString(), x.toString(), 0 )
      case HOLAtom( x, args ) => {
        ( x.toString() + "(" + ( args.foldRight( "" ) {
          case ( x, "" )  => "" + toAbbreviatedString( x )
          case ( x, str ) => toAbbreviatedString( x ) + ", " + str
        } ) + ")" + " : o", x.toString(), 0 )
      }
      case HOLFunction( x, args ) => {
        // if only 1 argument is provided
        // check if abbreviating of recursive function calls is possible
        if ( args.length == 1 ) {
          val p = pretty( args.head )

          // current function is equal to first and ONLY argument
          if ( p._2 == x.toString() ) {
            // increment counter and return (<current-string>, <functionsymbol>, <counter>)
            return ( p._1, x.toString(), p._3 + 1 )
          } // function symbol has changed from next to this level
          else {

            // in case of multiple recursive function calls
            if ( p._3 > 0 ) {
              return ( p._2 + "^" + p._3 + "(" + p._1 + ") : " + exp.exptype.toString(), x.toString(), 0 )
            } // otherwise
            else {
              return ( p._1, x.toString(), 1 )
            }
          }
        } else {
          return ( x.toString() + "(" + ( args.foldRight( "" ) {
            case ( x, "" ) => toAbbreviatedString( x )
            case ( x, s )  => toAbbreviatedString( x ) + ", " + s
          } ) + ") : " + exp.exptype.toString(), x.toString(), 0 )
        }

      }
      case _ => throw new Exception( "ERROR: Unknown HOL expression." );
    }
    return s

  }

}

/**
 * This is a prover9 style formatting which can be parsed by LLK.
 */
object toLLKString {
  def apply( e: LambdaExpression ) = toLatexString.getFormulaString( e, true, false )
}

/**
 * This is a Latex formatting which can be parsed by LLK.
 */
object toLatexString {
  def apply( e: LambdaExpression ) = getFormulaString( e, true, true )

  def getFormulaString( f: LambdaExpression, outermost: Boolean = true, escape_latex: Boolean ): String = f match {
    case All( x, t ) =>
      val op = if ( escape_latex ) "\\forall" else "all"
      "(" + op + " " + getFormulaString( x.asInstanceOf[Var], false, escape_latex ) + " " + getFormulaString( t, false, escape_latex ) + ")"
    case Ex( x, t ) =>
      val op = if ( escape_latex ) "\\exists" else "exists"
      "(" + op + " " + getFormulaString( x.asInstanceOf[Var], false, escape_latex ) + " " + getFormulaString( t, false, escape_latex ) + ")"
    case Neg( t1 ) =>
      val op = if ( escape_latex ) "\\neg" else "-"
      val str = " " + op + " " + getFormulaString( t1, false, escape_latex )
      if ( outermost ) str else "(" + str + ")"
    case And( t1, t2 ) =>
      val op = if ( escape_latex ) "\\land" else "&"
      val str = getFormulaString( t1, false, escape_latex ) + " " + op + " " + getFormulaString( t2, false, escape_latex )
      if ( outermost ) str else "(" + str + ")"
    case Or( t1, t2 ) =>
      val op = if ( escape_latex ) "\\lor" else "|"
      val str = getFormulaString( t1, false, escape_latex ) + " " + op + " " + getFormulaString( t2, false, escape_latex )
      if ( outermost ) str else "(" + str + ")"
    case Imp( t1, t2 ) =>
      val op = if ( escape_latex ) "\\rightarrow" else "->"
      val str = getFormulaString( t1, false, escape_latex ) + " " + op + " " + getFormulaString( t2, false, escape_latex )
      if ( outermost ) str else "(" + str + ")"

    case Var( v, _ )   => v.toString
    case Const( c, _ ) => c.toString
    case HOLAtom( f, args ) =>
      val sym = f match {
        case Const( x, _ ) => x
        case Var( x, _ )   => x
      }
      val str: String =
        if ( args.length == 2 && sym.toString.matches( """(<|>|\\leq|\\geq|=|>=|<=)""" ) ) {
          val str = getFormulaString( args( 0 ), false, escape_latex ) + " " + nameToLatexString( sym.toString ) + " " +
            getFormulaString( args( 1 ), false, escape_latex )
          if ( outermost ) str else "(" + str + ")"

        } else
          nameToLatexString( sym.toString ) + ( if ( args.isEmpty ) " " else args.map( getFormulaString( _, false, escape_latex ) ).mkString( "(", ", ", ")" ) )
      //if (outermost) str else "(" + str + ")"
      str
    case HOLFunction( f, args ) =>
      val sym = f match {
        case Const( x, _ ) => x
        case Var( x, _ )   => x
      }
      if ( args.length == 2 && sym.toString.matches( """[+\-*/]""" ) )
        "(" + getFormulaString( args( 0 ), false, escape_latex ) + " " + sym.toString + " " + getFormulaString( args( 1 ), false, escape_latex ) + ")"
      else {
        if ( args.isEmpty )
          nameToLatexString( sym.toString )
        else
          nameToLatexString( sym.toString ) + ( if ( args.isEmpty ) " " else args.map( getFormulaString( _, false, escape_latex ) ).mkString( "(", ", ", ")" ) )
      }
    // these cases need to be below the quantifiers and function/atom, since the latter are less general than abs/app
    case Abs( x, t ) =>
      "(\\lambda " + getFormulaString( x.asInstanceOf[Var], false, escape_latex ) + " " + getFormulaString( t, false, escape_latex ) + ")"
    case App( s, t ) =>
      if ( escape_latex )
        "\\apply{ " + getFormulaString( s, false, escape_latex ) + " " + getFormulaString( t, false, escape_latex ) + "}"
      else
        "(@ " + getFormulaString( s, false, escape_latex ) + " " + getFormulaString( t, false, escape_latex ) + ")"

  }

  def nameToLatexString( s: String, escapebrack: Boolean = true ): String = {
    val s1 = at.logic.gapt.utils.latex.nameToLatexString( s )
    //val s2 = if (escapebrack) s1.replaceAll("\\[","(").replaceAll("\\]",")") else s1
    val s2 = if ( s == "!=" ) "\\neq" else s1
    val s3 = if ( s2 != "-" ) s2.replaceAll( "-", "" ) else s2
    s3
  }
}

