package com.kieran.optiframe.protocol

/**
 * In-memory P1 frame model: the decoded protocol header plus the logical grid.
 *
 * The header is kept separate from the data canvas; physical control-band
 * placement is frozen by PC-007 and applied by the renderer.
 */
data class P1LogicalOpticalFrame(
    val header: OptiFrameGlobalHeader,
    val canvas: P1FrameCanvas
) {
    init {
        require(header.profileId == ProtocolConstants.PROFILE_P1_ID) {
            "P1LogicalOpticalFrame requires profile P1"
        }
    }

    val width: Int
        get() = canvas.width

    val height: Int
        get() = canvas.height

    fun symbolAt(x: Int, y: Int): Int? = canvas.symbolAt(x, y)

    fun roleAt(x: Int, y: Int): P1PhysicalLayout.CellRole =
        canvas.roleAt(x, y)
}
