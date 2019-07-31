package freenet.client.filter;

import java.io.IOException;
import java.util.ArrayList;

import freenet.support.Logger;
import freenet.support.Logger.LogLevel;

public class TheoraBitstreamFilter extends OggBitstreamFilter {
	private final TheoraPacketFilter parser;

	protected TheoraBitstreamFilter(OggPage page) {
		super(page);
		parser = new TheoraPacketFilter();
	}

	@Override
	OggPage parse(OggPage page) throws IOException {
		boolean logMINOR = Logger.shouldLog(LogLevel.MINOR, this);
		page = super.parse(page);
		if(!isValidStream) return null;
		ArrayList<CodecPacket> parsedPackets = new ArrayList<>();
		for(CodecPacket packet : page.asPackets()) {
			try {
				parsedPackets.add(parser.parse(packet));
			} catch (DataFilterException ignored) {
				// skip packet
			}
		}
		page = new OggPage(page, parsedPackets);
		return page;
	}
}
