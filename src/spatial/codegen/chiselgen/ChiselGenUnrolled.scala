package spatial.codegen.chiselgen

import argon.codegen.chiselgen.ChiselCodegen
import spatial.api.UnrolledExp


trait ChiselGenUnrolled extends ChiselCodegen {
  val IR: UnrolledExp
  import IR._

  private def emitUnrolledLoop(
    cchain: Exp[CounterChain],
    iters:  Seq[Seq[Bound[Index]]],
    valids: Seq[Seq[Bound[Bool]]]
  )(func: => Unit): Unit = {

    for (i <- iters.indices) {
      open(src"$cchain($i).foreach{case (is,vs) => ")
      iters(i).zipWithIndex.foreach{case (iter,j) => emit(src"val $iter = is($j)") }
      valids(i).zipWithIndex.foreach{case (valid,j) => emit(src"val $valid = vs($j)") }
    }
    func
    iters.indices.foreach{_ => close("}") }
  }

  private def flattenAddress(dims: Seq[Exp[Index]], indices: Seq[Exp[Index]]): String = {
    val strides = List.tabulate(dims.length){i => (dims.drop(i+1).map(quote) :+ "1").mkString("*") }
    indices.zip(strides).map{case (i,s) => src"$i*$s"}.mkString(" + ")
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case UnrolledForeach(cchain,func,iters,valids) =>
      emit(src"/** BEGIN UNROLLED FOREACH **/")
      open(src"val $lhs = {")
      emitUnrolledLoop(cchain, iters, valids){ emitBlock(func) }
      close("}")
      emit(src"/** END UNROLLED FOREACH **/")

    case UnrolledReduce(cchain,_,func,_,iters,valids,_) =>
      emit(src"/** BEGIN UNROLLED REDUCE **/")
      open(src"val $lhs = {")
      emitUnrolledLoop(cchain, iters, valids){ emitBlock(func) }
      close("}")
      emit(src"/** END UNROLLED REDUCE **/")

    case ParSRAMLoad(sram,inds) =>
      val dims = stagedDimsOf(sram)
      open(src"val $lhs = {")
      inds.indices.foreach{i =>
        emit(src"""val a$i = $sram.apply(${flattenAddress(dims, inds(i))})""")
      }
      emit(src"Array(" + inds.indices.map{i => src"a$i"}.mkString(", ") + ")")
      close("}")

    case ParSRAMStore(sram,inds,data,ens) =>
      val dims = stagedDimsOf(sram)
      open(src"val $lhs = {")
      inds.indices.foreach{i =>
        emit(src"if ($ens($i)) $sram.update(${flattenAddress(dims, inds(i))}, $data($i))")
      }
      close("}")

    case ParFIFODeq(fifo, ens, z) =>
      emit(src"val $lhs = $ens.map{en => if (en) $fifo.dequeue() else $z}")

    case ParFIFOEnq(fifo, data, ens) =>
      emit(src"val $lhs = $data.zip($ens).foreach{case (data, en) => if (en) $fifo.enqueue(data) }")

    case _ => super.emitNode(lhs, rhs)
  }
}