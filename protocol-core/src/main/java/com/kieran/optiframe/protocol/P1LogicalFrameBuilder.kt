package com.kieran.optiframe.protocol

/** Builds the in-memory logical representation of one P1 optical frame. */
object P1LogicalFrameBuilder {
    fun build(
        header: OptiFrameGlobalHeader,
        slots: List<P1LogicalFrameSlot>
    ): P1LogicalOpticalFrame {
        require(header.profileId == ProtocolConstants.PROFILE_P1_ID) {
            "Cannot build a P1 frame from profile ${header.profileId}"
        }

        val canvas = P1DataRegionComposer.compose(slots)
        return P1LogicalOpticalFrame(
            header = header,
            canvas = canvas
        )
    }
}
