/* Copyright 2009-2016 EPFL, Lausanne */

package inox
package solvers

import inox.grammars.GrammarsUniverse

trait SolverFactory {
  val program: Program

  type S <: Solver { val program: SolverFactory.this.program.type }

  val name: String

  def getNewSolver(): S

  def shutdown(): Unit = {}

  def reclaim(s: S) {
    s.free()
  }

  def toAPI = SimpleSolverAPI(this)
}

object SolverFactory {
  def create[S1 <: Solver](p: Program)(nme: String, builder: () => S1 { val program: p.type }):
           SolverFactory { val program: p.type; type S = S1 { val program: p.type } } = {
    new SolverFactory {
      val program: p.type = p
      type S = S1 { val program: p.type }

      val name = nme
      def getNewSolver() = builder()
    }
  }

  import evaluators._
  import combinators._

  val solverNames = Map(
    "nativez3" -> "Native Z3 with z3-templates for unrolling",
    "unrollz3" -> "Native Z3 with inox-templates for unrolling",
    "smt-cvc4" -> "CVC4 through SMT-LIB",
    "smt-z3"   -> "Z3 through SMT-LIB",
    "enum"     -> "Enumeration-based counter-example finder"
  )

  def getFromName(name: String)
                 (p: Program, opts: Options)
                 (ev: DeterministicEvaluator with SolvingEvaluator { val program: p.type },
                   enc: ast.ProgramEncoder { val sourceProgram: p.type; val t: inox.trees.type }):
                  SolverFactory { val program: p.type; type S <: TimeoutSolver { val program: p.type } } = {
    name match {
      case "nativez3" => create(p)(name, () => new {
        val program: p.type = p
        val options = opts
        val encoder = enc
      } with z3.NativeZ3Solver with TimeoutSolver {
        val evaluator = ev
      })

      case "unrollz3" => create(p)(name, () => new {
        val program: p.type = p
        val options = opts
        val encoder = enc
      } with unrolling.UnrollingSolver with theories.Z3Theories with TimeoutSolver {
        val evaluator = ev

        object underlying extends {
          val program: targetProgram.type = targetProgram
          val options = opts
        } with z3.UninterpretedZ3Solver
      })

      case "smt-cvc4" => create(p)(name, () => new {
        val program: p.type = p
        val options = opts
        val encoder = enc
      } with unrolling.UnrollingSolver with theories.CVC4Theories with TimeoutSolver {
        val evaluator = ev

        object underlying extends {
          val program: targetProgram.type = targetProgram
          val options = opts
        } with smtlib.CVC4Solver
      })

      case "smt-z3" => create(p)(name, () => new {
        val program: p.type = p
        val options = opts
        val encoder = enc
      } with unrolling.UnrollingSolver with theories.Z3Theories with TimeoutSolver {
        val evaluator = ev

        object underlying extends {
          val program: targetProgram.type = targetProgram
          val options = opts
        } with smtlib.Z3Solver
      })

      case "enum" => create(p)(name, () => new {
        val program: p.type = p
        val options = opts
      } with EnumerationSolver with TimeoutSolver {
        val evaluator = ev
        val grammars: GrammarsUniverse {val program: p.type} = new GrammarsUniverse {
          val program: p.type = p
        }
      })

      case _ => throw FatalError("Unknown solver: " + name)
    }
  }

  val solvers: Set[String] = solverNames.map(_._1).toSet

  def apply(name: String, p: InoxProgram, opts: Options): SolverFactory {
    val program: p.type
    type S <: TimeoutSolver { val program: p.type }
  } = getFromName(name)(p, opts)(RecursiveEvaluator(p, opts), ast.ProgramEncoder.empty(p))

  def apply(p: InoxProgram, opts: Options): SolverFactory {
    val program: p.type
    type S <: TimeoutSolver { val program: p.type }
  } = opts.findOptionOrDefault(optSelectedSolvers).toSeq match {
    case Seq() => throw FatalError("No selected solver")
    case Seq(single) => apply(single, p, opts)
    case multiple => PortfolioSolverFactory(p) {
      multiple.map(name => apply(name, p, opts))
    }
  }

  def default(p: InoxProgram): SolverFactory {
    val program: p.type
    type S <: TimeoutSolver { val program: p.type }
  } = apply(p, p.ctx.options)
}
