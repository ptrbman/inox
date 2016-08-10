/* Copyright 2009-2016 EPFL, Lausanne */

package inox.unit.solvers

import inox._
import inox.solvers._
import inox.solvers.combinators._

class SolverPoolSuite extends InoxTestSuite {
  import inox.trees._

  private trait DummySolver extends Solver {
    val name = "Dummy"
    val description = "dummy"

    def check: Option[Boolean] = None
    def assertCnstr(e: Expr) = {}
    def free() {}
    def reset() {}
    def getModel = ???
    def push() {}
    def pop() {}
    def interrupt() {}
    def recoverInterrupt() {}
  }

  def sfactory(implicit ctx: InoxContext): SolverFactory { val program: InoxProgram } = {
    val p = InoxProgram(ctx, new Symbols(Map.empty, Map.empty))
    SolverFactory.create(p)("dummy", () => new DummySolver {
      val program: p.type = p
    })
  }

  val poolSize = 5;

  test(s"SolverPool has at least $poolSize solvers") { implicit ctx =>

    val sp = new SolverPoolFactory(ctx, sfactory)

    var solvers = Set[Solver]()

    for (i <- 1 to poolSize) {
      solvers += sp.getNewSolver()
    }

    solvers.size === poolSize
  }

  test("SolverPool reuses solvers") { implicit ctx =>

    val sp = new SolverPoolFactory(ctx, sfactory)

    var solvers = Set[Solver]()

    for (i <- 1 to poolSize) {
      val s = sp.getNewSolver()
      solvers += s
      sp.reclaim(s)
    }

    for (i <- 1 to poolSize) {
      val s = sp.getNewSolver()
      assert(solvers contains s, "Solver not reused?")
      sp.reclaim(s)
    }
  }

  test(s"SolverPool can grow") { implicit ctx =>

    val sp = new SolverPoolFactory(ctx, sfactory)

    var solvers = Set[Solver]()

    for (i <- 1 to poolSize+3) {
      solvers += sp.getNewSolver()
    }

    solvers.size === poolSize+3
  }
}
