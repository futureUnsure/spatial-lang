package spatial.codegen.chiselgen

import argon.codegen.chiselgen.ChiselCodegen
import spatial.api.FILOExp
import spatial.api.DRAMTransferExp
import spatial.SpatialConfig
import spatial.SpatialExp

trait ChiselGenFILO extends ChiselGenSRAM {
  val IR: SpatialExp
  import IR._

  override protected def spatialNeedsFPType(tp: Type[_]): Boolean = tp match { // FIXME: Why doesn't overriding needsFPType work here?!?!
      case FixPtType(s,d,f) => if (s) true else if (f == 0) false else true
      case IntType()  => false
      case LongType() => false
      case FloatType() => true
      case DoubleType() => true
      case _ => super.needsFPType(tp)
  }

  override protected def bitWidth(tp: Type[_]): Int = {
    tp match { 
      case Bits(bitEv) => bitEv.length
      // case x: StructType[_] => x.fields.head._2 match {
      //   case _: IssuedCmd => 96
      //   case _ => super.bitWidth(tp)
      // }
      case _ => super.bitWidth(tp)
    }
  }

  override def quote(s: Exp[_]): String = {
    if (SpatialConfig.enableNaming) {
      s match {
        case lhs: Sym[_] =>
          lhs match {
            case Def(e: FILONew[_]) =>
              s"""x${lhs.id}_${nameOf(lhs).getOrElse("filo").replace("$","")}"""
            case Def(FILOPush(fifo:Sym[_],_,_)) =>
              s"x${lhs.id}_pushTo${fifo.id}"
            case Def(FILOPop(fifo:Sym[_],_)) =>
              s"x${lhs.id}_popFrom${fifo.id}"
            case Def(FILOEmpty(fifo:Sym[_])) =>
              s"x${lhs.id}_isEmpty${fifo.id}"
            case Def(FILOFull(fifo:Sym[_])) =>
              s"x${lhs.id}_isFull${fifo.id}"
            case Def(FILOAlmostEmpty(fifo:Sym[_])) =>
              s"x${lhs.id}_isAlmostEmpty${fifo.id}"
            case Def(FILOAlmostFull(fifo:Sym[_])) =>
              s"x${lhs.id}_isAlmostFull${fifo.id}"
            case Def(FILONumel(fifo:Sym[_])) =>
              s"x${lhs.id}_numel${fifo.id}"              
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

  override protected def remap(tp: Type[_]): String = tp match {
    case tp: FILOType[_] => src"chisel.collection.mutable.Queue[${tp.child}]"
    case _ => super.remap(tp)
  }

  // override protected def vecSize(tp: Type[_]): Int = tp.typeArguments.head match {
  //   case tp: Vector[_] => 1
  //   case _ => 1
  // }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@FILONew(size)   => 
      // ASSERT that all pars are the same!
      val rPar = readersOf(lhs).map { r => 
        r.node match {
          case Def(_: FILOPop[_]) => 1
          case Def(a@ParFILOPop(q,ens)) => ens.length
        }
      }.max
      val wPar = writersOf(lhs).map { w =>
        w.node match {
          case Def(_: FILOPush[_]) => 1
          case Def(a@ParFILOPush(q,_,ens)) => ens.length
        }
      }.max
      val width = bitWidth(lhs.tp.typeArguments.head)
      emitGlobalModule(s"""val ${quote(lhs)} = Module(new FILO($rPar, $wPar, $size, ${writersOf(lhs).length}, ${readersOf(lhs).length}, $width)) // ${nameOf(lhs).getOrElse("")}""")

    case FILOPush(fifo,v,en) => 
      val writer = writersOf(fifo).find{_.node == lhs}.get.ctrlNode
      // val enabler = if (loadCtrlOf(fifo).contains(writer)) src"${writer}_datapath_en" else src"${writer}_sm.io.output.ctr_inc"
      val enabler = src"${writer}_datapath_en"
      emit(src"""${fifo}.connectPushPort(Vec(List(${v}.r)), ${writer}_en & ($enabler & ~${writer}_inhibitor).D(${symDelay(lhs)}) & $en)""")


    case FILOPop(fifo,en) =>
      val reader = readersOf(fifo).find{_.node == lhs}.get.ctrlNode
      emit(src"val $lhs = Wire(${newWire(lhs.tp)})")
      emit(src"""${lhs}.r := ${fifo}.connectPopPort(${reader}_en & (${reader}_datapath_en & ~${reader}_inhibitor).D(${symDelay(lhs)}) & $en & ~${reader}_inhibitor).apply(0)""")

    case FILOEmpty(fifo) => emit(src"val $lhs = ${fifo}.io.empty")
    case FILOFull(fifo) => emit(src"val $lhs = ${fifo}.io.full")
    case FILOAlmostEmpty(fifo) => emit(src"val $lhs = ${fifo}.io.almostEmpty")
    case FILOAlmostFull(fifo) => emit(src"val $lhs = ${fifo}.io.almostFull")
    case FILONumel(fifo) => emit(src"val $lhs = ${fifo}.io.numel")

    case _ => super.emitNode(lhs, rhs)
  }
}
