HACOGen - hardware compressor generator


Hardware compressor generator (HACOGen), written in Chisel3, generates
a stall-free hardware compressor design (Verilog codes).  The current
version generates a simple zero-skimming compressor. The compressor
receives data as a vector of elements (each element is 16-bit by
default) every single cycle, compress the data and stack it into an
internal buffer. When the buffer gets full, the flush signal is
raised; its content is ready to be read.


Getting Started
---------------

     $ git clone https://github.com/kazutomo/hacogen.git
     $ cd hacogen
     $ make t


How to use HACOGen
--------------

     $ make test       # runs Scala test.
     $ make simulate   # invokes Verilator and generates vcd.
     $ make verilog    # generates Verilog codes.

Shorter target names for convinice

     $ make t
     $ make s
     $ make v

To test invididual module:

     $ make test T=selector

To list available target:

     $ make list
     Available targets: header, selector, squeeze, stbuf


Design RTL view
---------------

<img src="https://raw.githubusercontent.com/kazutomo/hacogen/master/figs/comp-rtl-view.png"  width="512" />


Simulation results
------------------

<img src="https://raw.githubusercontent.com/kazutomo/hacogen/master/figs/haco-wave.png"  width="512" />




----
Developed by Kazutomo Yoshii <kazutomo.yoshii@gmail.com>
