package rdma

import spinal.core._
import spinal.lib._
import spinal.lib.fsm._

import BusWidth.BusWidth
import Constants._

// Discard all invalid responses:
// - NAK with reserved code;
// - Target QP not exists;
// - Ghost ACK;
class RespVerifer(busWidth: BusWidth) extends Component {
  val io = new Bundle {
    val npsn = in(UInt(PSN_WIDTH bits))
    val rx = slave(Stream(Fragment(RdmaDataBus(busWidth))))
    val tx = master(Stream(Fragment(RdmaDataBus(busWidth))))
  }

  val validResp = False
  io.tx <-/< io.rx.continueWhen(validResp)
}

class RespHandler(busWidth: BusWidth) extends Component {
  val io = new Bundle {
    val npsn = in(UInt(PSN_WIDTH bits))
    val rx = slave(Stream(Fragment(RdmaDataBus(busWidth))))
    val qpStateUpdate = master(Stream(Bits(QP_STATE_WIDTH bits)))
    val cacheReq = master(Stream(CacheReq()))
    val cacheResp = slave(Stream(CacheData()))
    // Save read/atomic response data to main memory
    val dmaWriteReq = master(Stream(Fragment(DmaWriteReq())))
    val dmaWriteResp = slave(Stream(DmaWriteResp()))
  }

  val respVerifer = new RespVerifer(busWidth)
  respVerifer.io.rx <-/< io.rx
  val validRespRx = respVerifer.io.tx

  io.qpStateUpdate.valid := False
  io.qpStateUpdate.payload := QpState.ERR.id

  io.cacheReq <-/< validRespRx.translateWith {
    CacheReq().setDefaultVal()
  }
  io.cacheResp.ready := False

  io.dmaWriteReq <-/< io.dmaWriteResp.translateWith {
    val dmaWriteReq = Fragment(DmaWriteReq())
    dmaWriteReq.fragment.setDefaultVal()
    dmaWriteReq.last := False
    dmaWriteReq
  }
}
