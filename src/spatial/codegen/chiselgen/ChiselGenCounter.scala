package spatial.codegen.chiselgen

import argon.codegen.FileDependencies
import argon.codegen.chiselgen.ChiselCodegen
import spatial.api.CounterExp
import spatial.SpatialConfig
import spatial.SpatialExp


trait ChiselGenCounter extends ChiselCodegen with FileDependencies {
  val IR: CounterExp with SpatialExp
  import IR._

  // dependencies ::= AlwaysDep(s"${SpatialConfig.HOME}/src/spatial/codegen/chiselgen/resources/Counter.chisel")

  def emitCounterChain(lhs: Exp[_], ctrs: Seq[Exp[Counter]], suffix: String = ""): Unit = {
    val counter_data = ctrs.map{ c => c match {
      case Def(CounterNew(start, end, step, par)) => (src"$start", src"$end", src"$step", {src"$par"}.split('.').take(1)(0))
      case Def(Forever()) => ("0.U", "0.U", "0.U", "0")
    }}
    emitGlobal(src"""val ${lhs}${suffix}_en = Wire(Bool())""")
    emitGlobal(src"""val ${lhs}${suffix}_resetter = Wire(Bool())""")
    emit(src"""val ${lhs}${suffix}_strides = List(${counter_data.map(_._3).mkString(",")}) // TODO: Safe to get rid of this and connect directly?""")
    emit(src"""val ${lhs}${suffix}_maxes = List(${counter_data.map(_._2).mkString(",")}) // TODO: Safe to get rid of this and connect directly?""")
    emit(src"""val ${lhs}${suffix} = Module(new templates.Counter(List(${counter_data.map(_._4).mkString(",")}))) // Par of 0 creates forever counter""")
    emit(src"""${lhs}${suffix}.io.input.maxes.zip(${lhs}${suffix}_maxes).foreach { case (port,max) => port := max }""")
    emit(src"""${lhs}${suffix}.io.input.strides.zip(${lhs}${suffix}_strides).foreach { case (port,stride) => port := stride }""")
    emit(src"""${lhs}${suffix}.io.input.enable := ${lhs}${suffix}_ctr_en""")
    emit(src"""${lhs}${suffix}.io.input.reset := ${lhs}${suffix}_resetter""")
    emit(src"""val ${lhs}${suffix}_maxed = ${lhs}${suffix}.io.output.saturated""")
    ctrs.zipWithIndex.foreach { case (c, i) =>
      val x = c match {
        case Def(CounterNew(_,_,_,p)) => 
          val Const(xx: BigDecimal) = p
          xx
        case Def(Forever()) => 0
      }
      emit(s"""val ${quote(c)} = (0 until $x).map{ j => ${quote(lhs)}.io.output.counts($i + j) }""")
    }

  }

  override def quote(s: Exp[_]): String = {
    if (SpatialConfig.enableNaming) {
      s match {
        case lhs: Sym[_] =>
          val Op(rhs) = lhs
          rhs match {
            case CounterNew(s,e,st,p)=> 
              s"x${lhs.id}_ctr"
            case CounterChainNew(ctrs) =>
              s"x${lhs.id}_ctrchain"
            case _ =>
              super.quote(s)
          }
        case _ =>
          super.quote(s)
      }
    } else {
      super.quote(s)
    }
  } 

  override protected def remap(tp: Staged[_]): String = tp match {
    case CounterType      => src"Counter"
    case CounterChainType => src"Array[Counter]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case CounterNew(start,end,step,par) => 
      emit(s"// $lhs = ($start to $end by $step par $par")
    case CounterChainNew(ctrs) => 
      emitCounterChain(lhs, ctrs)
    case Forever() => 
      emit("// $lhs = Forever")

    case _ => super.emitNode(lhs, rhs)
  }

}
