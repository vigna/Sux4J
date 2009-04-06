package it.unimi.dsi.sux4j.io;

/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2009 Sebastiano Vigna 
 *
 *  This library is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as published by the Free
 *  Software Foundation; either version 2.1 of the License, or (at your option)
 *  any later version.
 *
 *  This library is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

import it.unimi.dsi.fastutil.bytes.ByteArrays;
import it.unimi.dsi.fastutil.io.FastBufferedInputStream;
import it.unimi.dsi.fastutil.io.FastBufferedInputStream.LineTerminator;
import it.unimi.dsi.fastutil.longs.AbstractLongIterator;
import it.unimi.dsi.fastutil.objects.AbstractObjectList;
import it.unimi.dsi.fastutil.objects.AbstractObjectListIterator;
import it.unimi.dsi.io.FileLinesCollection;
import it.unimi.dsi.io.SafelyCloseable;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.sux4j.util.EliasFanoMonotoneLongBigList;

import java.io.Closeable;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.EnumSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.RandomAccess;

/** A wrapper exhibiting the lines of a file as a {@linkplain List list}.
 * 
 * <P>An instance of this class allows to access the lines of a file as a
 * {@link List}. Contrarily to a {@link FileLinesCollection}, {@linkplain #get(int) direct access} 
 * is possible and reasonably efficient, in particular when accessing nearby lines, and
 * all returned {@linkplain MutableString mutable strings} are separate, independent instances.
 * 
 * <p>Similarly to {@link FileLinesCollection}, instead, {@link #iterator()} can be 
 * called any number of times, as it opens an independent input stream at each call. For the
 * same reason, the returned iterator type ({@link FileLinesList.FileLinesIterator})
 * is {@link java.io.Closeable}, and should be closed after usage.
 * 
 * <p>Note that {@link #toString()} will return a single string containing all
 * file lines separated by the string associated to the system property <samp>line.separator</samp>.
 * 
 * <p><strong>Warning</strong>: this class is not synchronised. Separate iterators use separate input
 * streams, and can be accessed concurrently, but all calls to {@link #get(int)} refer to the
 * same input stream.
 * 
 * <h2>Implementation details</h2>
 * 
 * <p>Instances of this class perform a full scan of the specified file at construction time, representing
 * the list of pointers to the start of each line using the {@linkplain EliasFanoMonotoneLongBigList Elias&ndash;Fano representation}. 
 * The memory occupation per line is thus bounded by 2 + log <var>l</var> bits, where <var>l</var> is the average line length.
 * 
 * @author Sebastiano Vigna
 * @since 1.1
 */
public class FileLinesList extends AbstractObjectList<MutableString> implements RandomAccess, Serializable {

	/** The filename upon which this file-lines collection is based. */
	private final String filename;
	/** The size of the list. */
	private final int size;
	/** The buffer size for all instances of {@link FastBufferedInputStream}. */
	private final int bufferSize;
	/** The terminators that must be used to separate the file lines. */
	private final EnumSet<LineTerminator> terminators;
	/** A byte buffer for character decoding. It is enough large to hold any line in the file. */
	private final ByteBuffer byteBuffer;
	/** A character buffer for character decoding. It is enough large to hold any line in the file. */
	private final CharBuffer charBuffer;
	/** A sparse selection structure keeping track of the start of each line in the file. */
	private EliasFanoMonotoneLongBigList borders;
	/** The fast buffered input stream used by {@link #get(int)}. */
	private final FastBufferedInputStream inputStream;
	/** A decoder used by {@link #get(int)}. */
	private final CharsetDecoder decoder;
	/** The charset specified at construction time. */
	private final Charset charset;
	
	/** Creates a file-lines collection for the specified filename with the specified encoding, buffer size and terminator set.
	 * 
	 * @param filename a filename.
	 * @param encoding an encoding.
	 * @param bufferSize the buffer size for {@link FastBufferedInputStream}.
	 * @param terminators a set of line terminators.
	 */		
	public FileLinesList( final CharSequence filename, final String encoding, final int bufferSize, final EnumSet<FastBufferedInputStream.LineTerminator> terminators ) throws IOException {
		this.bufferSize = bufferSize;
		this.terminators = terminators;
		this.filename = filename.toString();
		
		inputStream = new FastBufferedInputStream( new FileInputStream( this.filename ), bufferSize );
		decoder = ( charset = Charset.forName( encoding ) ).newDecoder();
		byte[] array = new byte[ 16 ];
		int count = 0, start, len;

		for(;;) {
			start = 0;
			while( ( len = inputStream.readLine( array, start, array.length - start, terminators ) ) == array.length - start ) {
				start += len;
				array = ByteArrays.grow( array, array.length + 1 );
			};
			
			if ( len != -1 ) count++;
			else break;
		}
		
		size = count;
		byteBuffer = ByteBuffer.wrap( array );
		charBuffer = CharBuffer.wrap( new char[ array.length ] );
		
		inputStream.position( 0 );
		borders = new EliasFanoMonotoneLongBigList( count, inputStream.length(), new AbstractLongIterator() {
			long pos = 0;
			byte[] buffer = byteBuffer.array();
			
			public boolean hasNext() {
				return pos < size;
			}
			
			public long nextLong() {
				if ( ! hasNext() ) throw new NoSuchElementException();
				pos++;
				try {
					final long result = inputStream.position();
					inputStream.readLine( buffer, terminators );
					return result;
				}
				catch ( IOException e ) {
					throw new RuntimeException( e );
				}
			}
		});
	}

	/** Creates a file-lines collection for the specified filename with the specified encoding, buffer size and with all terminators.
	 * 
	 * @param filename a filename.
	 * @param encoding an encoding.
	 * @param bufferSize the buffer size for {@link FastBufferedInputStream}.
	 */		
	public FileLinesList( final CharSequence filename, final String encoding, final int bufferSize ) throws IOException {
		this( filename, encoding, bufferSize, FastBufferedInputStream.ALL_TERMINATORS );
	}
		
	/** Creates a file-lines collection for the specified filename with the specified encoding, default buffer size and with all terminators.
	 * 
	 * @param filename a filename.
	 * @param encoding an encoding.
	 */		
	public FileLinesList( final CharSequence filename, final String encoding ) throws IOException {
		this( filename, encoding, FastBufferedInputStream.DEFAULT_BUFFER_SIZE );
	}
		
	public int size() {
		return size;
	}
		
	public MutableString get( final int index ) {
		return get( index, inputStream, byteBuffer, charBuffer, decoder );
	}
	
	public MutableString get( final int index, final FastBufferedInputStream fastBufferedInputStream, final ByteBuffer byteBuffer, final CharBuffer charBuffer, final CharsetDecoder decoder ) {
		try {
			fastBufferedInputStream.position( borders.getLong( index ) );
			byteBuffer.clear();
			byteBuffer.limit( fastBufferedInputStream.readLine( byteBuffer.array(), terminators ) );
			charBuffer.clear();
			decoder.decode( byteBuffer, charBuffer, true );
			return new MutableString( charBuffer.array(), 0, charBuffer.position() );
		}
		catch ( IOException e ) {
			throw new RuntimeException( e );
		}
	}

	/** An iterator over the lines of a {@link FileLinesList}. Instances of this
	 * class open an {@link java.io.InputStream}, and thus should be {@linkplain Closeable#close() closed} after
	 * usage. A &ldquo;safety-net&rdquo; finaliser tries to take care of the cases in which
	 * closing an instance is impossible.
	 */

	public static final class FileLinesIterator extends AbstractObjectListIterator<MutableString> implements SafelyCloseable {
		/** An fast buffered input stream used exclusively by this iterator. */
		private FastBufferedInputStream inputStream;
		/** A byte buffer used exclusively by this iterator. */
		private final ByteBuffer byteBuffer;
		/** A character buffer used exclusively by this iterator. */
		private final CharBuffer charBuffer;
		/** A charset decoder used exclusively by this iterator. */
		private final CharsetDecoder decoder;
		/** The list of file lines associated to this iterator. */
		private final FileLinesList fileLinesList;
		/** The current position (line) in the file. */
		private int pos;

		protected FileLinesIterator( final FileLinesList fileLinesList, final int index, final FastBufferedInputStream inputStream, final CharsetDecoder decoder, final ByteBuffer byteBuffer, final CharBuffer charBuffer ) {
			this.inputStream = inputStream;
			this.decoder = decoder;
			this.byteBuffer = byteBuffer;
			this.charBuffer = charBuffer;
			this.fileLinesList = fileLinesList;
			pos = index;
		}

		public boolean hasNext() {
			return pos < fileLinesList.size;
		}

		public boolean hasPrevious() {
			return pos > 0;
		}

		public MutableString next() {
			if ( !hasNext() ) throw new NoSuchElementException();
			return fileLinesList.get( pos++, inputStream, byteBuffer, charBuffer, decoder );
		}

		public MutableString previous() {
			if ( !hasPrevious() ) throw new NoSuchElementException();
			return fileLinesList.get( --pos, inputStream, byteBuffer, charBuffer, decoder );
		}

		public int nextIndex() {
			return pos;
		}

		public int previousIndex() {
			return pos - 1;
		}

		public synchronized void close() {
			if ( inputStream == null ) throw new IllegalStateException();
			try {
				inputStream.close();
			}
			catch ( IOException e ) {
				throw new RuntimeException( e );
			}
			finally {
				inputStream = null;
			}
		}
		
		protected synchronized void finalize() throws Throwable {
			try {
				if ( inputStream != null ) close();
			}
			finally {
				super.finalize();
			}
		}
	}
	
	public FileLinesIterator listIterator( final int index ) {
		try {
			return new FileLinesIterator( this, index, new FastBufferedInputStream( new FileInputStream( filename ), bufferSize ), charset.newDecoder(), ByteBuffer.wrap( new byte[ byteBuffer.array().length ] ), CharBuffer.wrap( new char[ charBuffer.array().length ] ) );
		}
		catch ( FileNotFoundException e ) {
			throw new RuntimeException( e );
		}
	}

	public String toString() {
		final MutableString separator = new MutableString( System.getProperty( "line.separator" ) );
		final MutableString s = new MutableString();
		for( MutableString l: this ) s.append( l ).append( separator );
		return s.toString();
	}
}
