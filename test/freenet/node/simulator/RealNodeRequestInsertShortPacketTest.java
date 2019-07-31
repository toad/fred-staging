package freenet.node.simulator;

import java.io.IOException;

import org.junit.Ignore;
import org.junit.Test;

import freenet.io.comm.PeerParseException;
import freenet.io.comm.ReferenceSignatureVerificationException;
import freenet.keys.CHKEncodeException;
import freenet.keys.KeyDecodeException;
import freenet.keys.SSKEncodeException;
import freenet.node.FSParseException;
import freenet.node.NodeInitException;
import freenet.node.NodeStarter.TestingVMBypass;
import freenet.node.simulator.RealNodeRequestInsertTester.ExitException;
import freenet.node.simulator.RealNodeTester.SimulatorOverloadedException;
import freenet.support.TestProperty;
import freenet.support.LoggerHook.InvalidThresholdException;
import freenet.support.compress.InvalidCompressionCodecException;

public class RealNodeRequestInsertShortPacketTest extends RealNodeRequestInsertShortTestBase {

    @Test @Ignore
    public void testSmallNetwork() throws CHKEncodeException, SSKEncodeException, FSParseException, PeerParseException, InvalidThresholdException, NodeInitException, ReferenceSignatureVerificationException, InterruptedException, SimulatorOverloadedException, InvalidCompressionCodecException, IOException, KeyDecodeException, ExitException {
        //if(!TestProperty.EXTENSIVE) return;
        // Bandwidth limit is arbitrary but must be set small enough to simulate delays between 
        // blocks etc, and to not break slow CI VMs running the unit tests.
        RealNodeRequestInsertTester.BWLIMIT = 32*1024;
        testSmallNetwork(TestingVMBypass.PACKET_BYPASS);
    }
    
}
