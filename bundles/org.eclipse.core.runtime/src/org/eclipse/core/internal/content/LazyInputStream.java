/*******************************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.internal.content;

import java.io.IOException;
import java.io.InputStream;

public class LazyInputStream extends InputStream {
	private int blockCapacity;
	byte[][] blocks = {};
	private int bufferSize;
	private InputStream in;
	private int mark;
	private int offset;

	public LazyInputStream(InputStream in, int blockCapacity) {
		this.in = in;
		this.blockCapacity = blockCapacity;
	}

	public int available() throws IOException {
		return bufferSize - offset + in.available();
	}

	private int copyFromBuffer(byte[] userBuffer, int userOffset, int needed) {
		int copied = 0;
		int current = offset / blockCapacity;
		while ((needed - copied) > 0 && current < blocks.length) {
			int blockSize = computeBlockSize(current);
			int offsetInBlock = offset % blockCapacity;
			int availableInBlock = blockSize - offsetInBlock;
			int toCopy = Math.min(availableInBlock, needed - copied);
			System.arraycopy(blocks[current], offsetInBlock, userBuffer, userOffset + copied, toCopy);
			copied += toCopy;
			current++;
			offset += toCopy;
		}
		return copied;
	}

	private void ensureAvailable(long bytesToRead) throws IOException {
		int loadedBlockSize = blockCapacity;
		while (bufferSize < offset + bytesToRead && loadedBlockSize == blockCapacity) {
			loadedBlockSize = loadBlock();
			bufferSize += loadedBlockSize;
		}
	}

	// for testing purposes
	protected int getBlockCount() {
		return blocks.length;
	}

	private int computeBlockSize(int blockIndex) {
		if (blockIndex < blocks.length - 1)
			return blockCapacity;
		int blockSize = bufferSize % blockCapacity;
		return blockSize == 0 ? blockCapacity : blockSize;
	}

	// for testing purposes
	protected int getBufferSize() {
		return bufferSize;
	}

	// for testing purposes
	protected int getMark() {
		return mark;
	}

	// for testing purposes
	protected int getOffset() {
		return offset;
	}

	private int loadBlock() throws IOException {
		// read a block from the underlying stream
		byte[] newBlock = new byte[blockCapacity];
		int readCount = in.read(newBlock);
		if (readCount == -1)
			return 0;
		// expand blocks array
		byte[][] tmpBlocks = new byte[blocks.length + 1][];
		System.arraycopy(blocks, 0, tmpBlocks, 0, blocks.length);
		blocks = tmpBlocks;
		blocks[blocks.length - 1] = newBlock;
		return readCount;
	}

	public void mark(int readlimit) {
		mark = offset;
	}

	public boolean markSupported() {
		return true;
	}

	public int read() throws IOException {
		ensureAvailable(1);
		if (bufferSize <= offset)
			return -1;
		int nextByte = 0xFF & blocks[offset / blockCapacity][offset % blockCapacity];
		offset++;
		return nextByte;
	}

	public int read(byte[] b) throws IOException {
		return read(b, 0, b.length);
	}

	public int read(byte[] b, int off, int len) throws IOException {
		ensureAvailable(len);
		int copied = copyFromBuffer(b, off, len);
		return copied == 0 ? -1 : copied;
	}

	public void reset() {
		offset = mark;
		mark = 0;
	}

	public long skip(long toSkip) throws IOException {
		if (toSkip <= 0)
			return 0;
		ensureAvailable(toSkip);
		long skipped = Math.min(toSkip, bufferSize - offset);
		offset += skipped;
		return skipped;
	}
}