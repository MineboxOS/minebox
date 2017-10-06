package io.minebox.nbd.transmission;

import io.netty.buffer.ByteBuf;

class OperationParameters {

    static final OperationParameters LIMBO_STATE = new OperationParameters(State.LIMBO);
    static final OperationParameters RECEIVE_STATE = new OperationParameters(State.TM_RECEIVE_CMD);

    final short cmdFlags;
    final short cmdType;
    final long cmdHandle;
    final long cmdOffset;
    final long cmdLength;
    final State state;

    private OperationParameters(ByteBuf message) {
        cmdFlags = message.readShort();
        cmdType = message.readShort();
        cmdHandle = message.readLong();
        cmdOffset = message.readLong();
        cmdLength = message.readUnsignedInt();
        state = State.TM_RECEIVE_CMD_DATA;
    }

    private OperationParameters(State state) {
        this.state = state;
        cmdFlags = -1;
        cmdType = -1;
        cmdHandle = -1;
        cmdOffset = -1;
        cmdLength = -1;
    }

    static OperationParameters readFromMessage(ByteBuf message) {
        return new OperationParameters(message);
    }
}
