/* Copyright 2009-2016 EPFL, Lausanne */

package inox
package grammars

trait FunctionCallsGrammars extends utils.Helpers { self: GrammarsUniverse =>
  import program._
  import trees._
  import exprOps._
  import symbols._

  /** Generates non-recursive function calls
    *
    * @param currentFunction The currend function for which no calls will be generated
    * @param types The candidate real type parameters for [[currentFunction]]
    * @param exclude An additional set of functions for which no calls will be generated
    */
  case class FunctionCalls(currentFunction: FunDef, types: Seq[Type], exclude: Set[FunDef]) extends SimpleExpressionGrammar {
    def computeProductions(t: Type): Seq[Prod] = {

      def getCandidates(fd: FunDef): Seq[TypedFunDef] = {
        // Prevents recursive calls
        val cfd = currentFunction

        val isRecursiveCall = (transitiveCallers(cfd) + cfd) contains fd

        val isDet = true // TODO FIXME fd.body.exists(isDeterministic)

        if (!isRecursiveCall && isDet) {
          canBeSubtypeOf(fd.returnType, t) match {
            case Some(tpsMap) =>
              val free = fd.tparams.map(_.tp)
              val tfd = fd.typed(free.map(tp => tpsMap.getOrElse(tp, tp)))

              if (tpsMap.size < free.size) {
                /* Some type params remain free, we want to assign them:
                 *
                 * List[T] => Int, for instance, will be found when
                 * requesting Int, but we need to assign T to viable
                 * types. For that we use list of input types as heuristic,
                 * and look for instantiations of T such that input <?:
                 * List[T].
                 */
                types.distinct.flatMap { (atpe: Type) =>
                  var finalFree = free.toSet -- tpsMap.keySet
                  var finalMap = tpsMap

                  for (ptpe <- tfd.params.map(_.getType).distinct) {
                    unify(atpe, ptpe, finalFree.toSeq) match { // FIXME!!!! this may allow weird things if lub!=ptpe
                      case Some(ntpsMap) =>
                        finalFree --= ntpsMap.keySet
                        finalMap  ++= ntpsMap
                      case _ =>
                    }
                  }

                  if (finalFree.isEmpty) {
                    List(fd.typed(free.map(tp => finalMap.getOrElse(tp, tp))))
                  } else {
                    Nil
                  }
                }
              } else {
                // All type parameters that used to be free are assigned
                List(tfd)
              }
            case None =>
              Nil
          }
        } else {
          Nil
        }
      }

      val filter = (tfd:TypedFunDef) => /* TODO: Reimplement this somehow tfd.fd.isSynthetic || tfd.fd.isInner || */ (exclude contains tfd.fd)

      val funcs = functionsAvailable.toSeq.sortBy(_.id).flatMap(getCandidates).filterNot(filter)

      funcs.map{ tfd =>
        nonTerminal(tfd.params.map(_.getType), FunctionInvocation(tfd.id, tfd.tps, _))//, tagOf(tfd.fd, isSafe = false))
      } 

    }
  }

}

