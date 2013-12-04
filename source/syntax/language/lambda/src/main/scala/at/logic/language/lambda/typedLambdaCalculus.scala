/*
 * typedLambdaCalculus.scala
 *
 */

package at.logic.language.lambda

import symbols._
import types._

// Collects all methods that operate on LambdaExpressions
abstract class LambdaExpression {
  
  // Expression type [should it be here?]
  def exptype: TA

  // Syntactic equality
  def syntaxEquals(e: LambdaExpression): Boolean

  // Alpha equality
  def alphaEquals(a: Any, subs: Map[Var, Var]): Boolean

  // Factory for Lambda-Expressions
  def factory : FactoryA = LambdaFactory

}

// Defines the elements that generate lambda-expressions: variables,
// applications and abstractions (and constants).

class Var(val sym: SymbolA, val exptype: TA) extends LambdaExpression {

  // The name of the variable should be obtained with this method.
  def name : String = sym.toString

  override def equals(a: Any) = alphaEquals(a, Map[Var, Var]())

  // Syntactic equality: two variables are equal if they have the same name and the same type
  def syntaxEquals(e: LambdaExpression) = e match {
    case Var(n, t) => (n == name && t == exptype)
    case _ => false
  }

  // Alpha-equality
  // Two free variables are *not* alpha-equivalent if they don't have the same
  // name and type or if they are not in the substitution list because of a
  // binding.
  def alphaEquals(a: Any, subs: Map[Var, Var]) = a match {
    case Var(n, t) if !subs.contains(this) => (n == name && t == exptype)
    case v: Var if subs(this).syntaxEquals(v) => true 
    case _ => false
  }
    
  // Printing
  override def toString() = "Var(" + name + "," + exptype + ")"

  override def hashCode() = (41 * name.hashCode) + exptype.hashCode
}
object Var {
  def apply(name: String, exptype: TA) = LambdaFactory.createVar(StringSymbol(name), exptype)
  def apply(name: String, exptype: String) = LambdaFactory.createVar(StringSymbol(name), Type(exptype))
  def apply(sym: SymbolA, exptype: TA) = LambdaFactory.createVar(sym, exptype)
  def apply(sym: SymbolA, exptype: String) = LambdaFactory.createVar(sym, Type(exptype))
  def unapply(e: LambdaExpression) = e match {
    case v : Var => Some(v.name, v.exptype)
    case _ => None
  }
}

class Const(val sym: SymbolA, val exptype: TA) extends LambdaExpression {

  // The name of the variable should be obtained with this method.
  def name : String = sym.toString

  override def equals(a: Any) = alphaEquals(a, Map[Var, Var]())

  // Syntactic equality
  def syntaxEquals(e: LambdaExpression) = e match {
    case Const(n, t) => (n == name && t == exptype)
    case _ => false
  }
    
  // Alpha-equality
  // Two constants are *not* alpha-equivalent if they don't have the same name and type.
  def alphaEquals(a: Any, subs: Map[Var, Var]) = a match {
    case Const(n, t) => n == name && t == exptype
    case _ => false
  }
  
  // Printing
  override def toString() = "Cons(" + name + "," + exptype + ")"

  override def hashCode() = (41 * name.hashCode) + exptype.hashCode
}
object Const {
  def apply(name: String, exptype: TA) = LambdaFactory.createConst(StringSymbol(name), exptype)
  def apply(name: String, exptype: String) = LambdaFactory.createConst(StringSymbol(name), Type(exptype))
  def apply(sym: SymbolA, exptype: TA) = LambdaFactory.createConst(sym, exptype)
  def apply(sym: SymbolA, exptype: String) = LambdaFactory.createConst(sym, Type(exptype))
  def unapply(e: LambdaExpression) = e match {
    case c : Const => Some(c.name, c.exptype)
    case _ => None
  }
}

class App(val function: LambdaExpression, val arg: LambdaExpression) extends LambdaExpression {
  
  // Making sure that if f: t1 -> t2 then arg: t1
  require({
    function.exptype match {
      case ->(in,out) => {
        if (in == arg.exptype) true
        else false
      }
      case _ => false
    }
  }, "Types don't fit while constructing application " + function + " " + arg)

  // Application type (if f: t1 -> t2 and arg: t1 then t2)
  def exptype: TA = {
    function.exptype match {
        case ->(in,out) => out
    }
  }
 
  override def equals(a: Any) = alphaEquals(a, Map[Var, Var]())

  // Syntactic equality
  def syntaxEquals(e: LambdaExpression) = e match {
    case App(a,b) => (a.syntaxEquals(function) && b.syntaxEquals(arg) && e.exptype == exptype)
    case _ => false
  }

  // Alpha-equality
  // An application is alpha-equivalent if its terms are alpha-equivalent.
  def alphaEquals(a: Any, subs: Map[Var, Var]) = a match {
    case App(e1, e2) => e1.alphaEquals(function, subs) && e2.alphaEquals(arg, subs)
    case _ => false
  }

  // Printing
  override def toString() = "App(" + function + "," + arg + ")"
  
  override def hashCode() = (41 * function.hashCode) + arg.hashCode
}
object App {
  def apply(f: LambdaExpression, a: LambdaExpression) = a.factory.createApp(f, a)
  // create an n-ary application with left-associative parentheses
  def apply(function: LambdaExpression, arguments:List[LambdaExpression]): LambdaExpression = arguments match {
    case Nil => function
    case x::ls => apply(App(function, x), ls)
  }
  def unapply(e: LambdaExpression) = e match {
    case a : App => Some((a.function, a.arg))
    case _ => None
  }
}

class Abs(val variable: Var, val term: LambdaExpression) extends LambdaExpression {

  // Abstraction type construction based on the types of the variable and term
  def exptype: TA = ->(variable.exptype, term.exptype)
  
  override def equals(a: Any) = alphaEquals(a, Map[Var, Var]())
  
  // Syntactic equality
  def syntaxEquals(e: LambdaExpression) = e match {
    case Abs(v, exp) => (v.syntaxEquals(variable) && exp.syntaxEquals(term) && e.exptype == exptype)
    case _ => false
  }

  // Alpha-equality
  // Two abstractions are alpha-equivalent if the terms are equivalent up to the
  // renaming of variables.
  def alphaEquals(a: Any, subs: Map[Var, Var]) = a match {
    case Abs(v, t) =>
      if (v.exptype == variable.exptype) {
        t.alphaEquals(term, subs + (variable -> v) + (v -> variable))
      }
      else false
    case _ => false
  }

  // Printing
  override def toString() = "Abs(" + variable + "," + term + ")"
  
  override def hashCode() = (41 * variable.hashCode) + term.hashCode
}
object Abs {
  def apply(v: Var, t: LambdaExpression) = t.factory.createAbs(v, t)
  def apply(variables: List[Var], expression: LambdaExpression): LambdaExpression = variables match {
    case Nil => expression
    case x::ls => Abs(x, apply(ls, expression))
  }
  def unapply(e: LambdaExpression) = e match {
    case a : Abs => Some((a.variable, a.term))
    case _ => None
  }
}

/*********************** Factories *****************************/

trait FactoryA {
  def createVar( name: SymbolA, exptype: TA ) : Var
  def createConst( name: SymbolA, exptype: TA ) : Const
  def createAbs( variable: Var, exp: LambdaExpression ) : Abs
  def createApp( fun: LambdaExpression, arg: LambdaExpression ) : App
}

object LambdaFactory extends FactoryA {
  def createVar( name: SymbolA, exptype: TA )  = new Var( name, exptype)
  def createConst( name: SymbolA, exptype: TA )  = new Const( name, exptype)
  def createAbs( variable: Var, exp: LambdaExpression ) = new Abs( variable, exp )
  def createApp( fun: LambdaExpression, arg: LambdaExpression ) = new App( fun, arg )
}
