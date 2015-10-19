package at.logic.gapt.proofs.lkNew

import at.logic.gapt.expr._
import at.logic.gapt.proofs._
import org.specs2.mutable._

class AtomicExpansionTest extends Specification {

  def test( f: HOLFormula ) = AtomicExpansion( f ).endSequent must_== ( f +: Sequent() :+ f )

  "atomic expansion" should {
    val Seq( p, q ) = Seq( "p", "q" ) map { FOLAtom( _ ) }
    val r = FOLAtomHead( "r", 2 )
    val Seq( x, y ) = Seq( "x", "y" ) map { FOLVar( _ ) }

    "atom" in { test( p ) }
    "top" in { test( Top() ) }
    "bottom" in { test( Bottom() ) }
    "neg" in { test( -p ) }
    "and" in { test( p & r( x, y ) ) }
    "or" in { test( p | q ) }
    "imp" in { test( p --> q ) }
    "all" in { test( All( x, r( x, y ) ) ) }
    "ex" in { test( Ex( x, r( x, y ) ) ) }
    "non-vnf" in { test( Ex( x, r( x, y ) & All( x, Ex( y, r( x, y ) ) ) ) ) }
  }

}