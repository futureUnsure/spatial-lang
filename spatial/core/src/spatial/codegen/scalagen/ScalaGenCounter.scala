package spatial.codegen.scalagen

import argon.codegen.FileDependencies
import argon.codegen.scalagen.ScalaCodegen
import spatial.api.CounterExp
import spatial.{SpatialConfig, SpatialExp}

trait ScalaGenCounter extends ScalaCodegen with FileDependencies {
  val IR: SpatialExp
  import IR._

  dependencies ::= FileDep("scalagen", "Counter.scala")

  override protected def remap(tp: Type[_]): String = tp match {
    case CounterType      => src"Counterlike"
    case CounterChainType => src"Array[Counterlike]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case CounterNew(start,end,step,par) => emit(src"val $lhs = Counter($start, $end, $step, $par)")
    case CounterChainNew(ctrs) => emit(src"""val $lhs = Array(${ctrs.map(quote).mkString(",")})""")
    case Forever() => emit(src"""val $lhs = Forever()""")
    case _ => super.emitNode(lhs, rhs)
  }

}
