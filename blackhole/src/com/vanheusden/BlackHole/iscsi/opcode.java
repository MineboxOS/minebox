/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.iscsi;

public enum opcode {
	// initiators
	oNOP_Out(0x00),
	oSCSI_Command(0x01),
	oSCSI_Task_Management_function_request(0x02),
	oLogin_Request(0x03),
	oText_Request(0x04),
	oSCSI_Data_Out(0x05),
	oLogout_Request(0x06),
	oSNACK_Request(0x10),
	oVendor_1c(0x1c),
	oVendor_1d(0x1d),
	oVendor_1e(0x1e),
	// targets
	oNOP_In(0x20),
	oSCSI_Response(0x21),
	oSCSI_Task_Management_function_response(0x22),
	oLogon_Response(0x23),
	oText_Response(0x24),
	oSCSI_Data_In(0x25),
	oLogout_Response(0x26),
	oReady_To_Transfer(0x31),
	oAsynchronous_Message(0x32),
	oVendor_3c(0x3c),
	oVendor_3d(0x3d),
	oVendor_3e(0x3e),
	oReject(0x3f);

	int nr;
	opcode(int nr) {
		this.nr = nr;
	}

};
