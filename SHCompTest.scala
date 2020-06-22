//
// HACOGen tester
//
// written by Kazutomo Yoshii <kazutomo.yoshii@gmail.com>
//
package hacogen

import chisel3.iotesters
import chisel3.iotesters.{Driver, PeekPokeTester}
import scala.collection.mutable.ListBuffer

class SHCompUnitTester(c: SHComp) extends PeekPokeTester(c) {
  // SHComp
  // val sh_nelems_src:Int = 16
  // val sh_elemsize:Int = 9
  // val nelems_dst:Int = 28

  println(f"sh_nelems_src=${c.sh_nelems_src}")
  println(f"sh_elemsize=${c.elemsize}")
  println(f"nelems_dst=${c.nelems_dst}")

  // TODO: fill an actual test here after CompTest gets updated
  //

  // SHComp:
  //
  // val in  = Input(Vec(sh_nelems_src, UInt(sh_elemsize.W)))
  // val out = Output(Vec(nelems_dst, UInt(elemsize.W)))
  //
  // below are debuginfo
  // val bufsel = Output(UInt(1.W))
  // val bufpos = Output(UInt(log2Ceil(nelems_dst).W))
  // val flushed = Output(Bool())
  // val flushedbuflen = Output(UInt(log2Ceil(nelems_dst+1).W))
  //

  println("")
  println("Currently no test is implemented...")
}
