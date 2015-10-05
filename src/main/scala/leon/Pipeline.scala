/* Copyright 2009-2015 EPFL, Lausanne */

package leon

abstract class Pipeline[-F, +T] {
  self =>

  def andThen[G](thenn: Pipeline[T, G]): Pipeline[F, G] = new Pipeline[F,G] {
    def run(ctx: LeonContext, v: F): (LeonContext, G) = {
      val (ctx2, s) = self.run(ctx, v)
      if(ctx.findOptionOrDefault(SharedOptions.optStrictPhases)) ctx.reporter.terminateIfError()
      thenn.run(ctx2, s)
    }
  }

  def run(ctx: LeonContext, v: F): (LeonContext, T)
}

object Pipeline {
  
  def both[T, R1, R2](f1: Pipeline[T, R1], f2: Pipeline[T, R2]): Pipeline[T, (R1, R2)] = new Pipeline[T, (R1, R2)] {
    def run(ctx: LeonContext, t: T): (LeonContext, (R1, R2)) = {
      val (ctx1, r1) = f1.run(ctx, t)
      // don't check for SharedOptions.optStrictPhases because f2 does not depend on the result of f1
      val (ctx2, r2) = f2.run(ctx1, t)
      (ctx2, (r1, r2))
    }
  }
  
}
