/*
 * Java core library component.
 *
 * Copyright (c) 1997, 1998
 *      Transvirtual Technologies, Inc.  All rights reserved.
 *
 * See the file "license.terms" for information on usage and redistribution
 * of this file.
 */


package java.awt;

import java.awt.image.ColorModel;
import java.awt.image.DirectColorModel;
import java.awt.image.ImageConsumer;
import java.awt.image.ImageObserver;
import java.awt.image.ImageProducer;
import java.awt.image.IndexColorModel;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import kaffe.io.TmpFile;
import kaffe.util.Ptr;

public class Image
{
	Ptr nativeData;
	int width = -1;
	int height = -1;
	ImageProducer producer;
	Object srcObj;
	Vector observers = new Vector();
	int flags;
	final static int PRODUCING = 1 << 8;
	final static int READY = ImageObserver.WIDTH | ImageObserver.HEIGHT | ImageObserver.ALLBITS;
	final static int SCREEN = 1 << 10;
	static Thread prodThread;
	static Image.Initializer prodQueue;
	static Object lock = new Object();

class NativeProducer
  implements ImageProducer
{
	Vector consumers = new Vector(1);

public void addConsumer ( ImageConsumer consumer ) {
	if ( !consumers.contains( consumer) )
		consumers.addElement( consumer);
}

void imageComplete ( int status ) {
	ImageConsumer c;
	Enumeration   e;
	
	for ( e=consumers.elements(); e.hasMoreElements(); ){
		c = (ImageConsumer) e.nextElement();
		c.imageComplete( status);
	}
}

public boolean isConsumer ( ImageConsumer consumer ){
	return consumers.contains( consumer);
}

void produceFromFile () {
	createFromFile();
	
	if ( nativeData != null ) {
		produceFromNativeImage();
	}
	else {
		imageComplete( ImageConsumer.IMAGEERROR | ImageConsumer.IMAGEABORTED);
	}
}

void produceFromNativeImage () {
	// this will take care of the neccessary notifications
	Toolkit.imgProduceImage( this, nativeData);
}

void produceFromURL () {
	createFromURL();
	
	if ( nativeData != null ) {
		produceFromNativeImage();
	}
	else {
		imageComplete( ImageConsumer.IMAGEERROR | ImageConsumer.IMAGEABORTED);
	}
}

public void removeConsumer ( ImageConsumer consumer ){
	consumers.removeElement( consumer);
}

public void requestTopDownLeftRightResend ( ImageConsumer consumer ){
}

void setColorModel ( ColorModel cm ) {
	ImageConsumer c;
	Enumeration   e;
	
	for ( e=consumers.elements(); e.hasMoreElements(); ){
		c = (ImageConsumer) e.nextElement();
		c.setColorModel( cm);
	}
}

void setDimensions ( int w, int h ) {
	ImageConsumer c;
	Enumeration   e;
	
	for ( e=consumers.elements(); e.hasMoreElements(); ){
		c = (ImageConsumer) e.nextElement();
		c.setDimensions( w, h);
	}
}

void setHints ( int hints ) {
	ImageConsumer c;
	Enumeration   e;
	
	for ( e=consumers.elements(); e.hasMoreElements(); ){
		c = (ImageConsumer) e.nextElement();
		c.setHints( hints);
	}
}

void setPixels ( int x, int y, int w, int h,
                        ColorModel cm, int[] pixels, int off, int ssize ) {
	ImageConsumer c;
	Enumeration   e;
	
	for ( e=consumers.elements(); e.hasMoreElements(); ){
		c = (ImageConsumer) e.nextElement();
		c.setPixels( x, y, w, h, cm, pixels, off, ssize);
	}
}

public void startProduction ( ImageConsumer consumer ) {
	addConsumer( consumer);
	
	if ( nativeData != null ) {
		produceFromNativeImage();
	}
	else {
		if ( srcObj != null ) {
			if ( srcObj instanceof String ) 
				produceFromFile();
			else if ( srcObj instanceof URL )
				produceFromURL();
		}
	}
}
}
class Initializer
  implements ImageConsumer, Runnable
{
	Initializer next;

public void imageComplete ( int state ) {
	int s = 0;

	if ( state == SINGLEFRAMEDONE )		 s |= ImageObserver.FRAMEBITS;
	if ( state == STATICIMAGEDONE )    s |= ImageObserver.ALLBITS;
	if ( state == IMAGEERROR )         s |= ImageObserver.ERROR;
	if ( state == IMAGEABORTED )       s |= ImageObserver.ABORT;

  stateChange( flags | s, 0, 0, width, height);
}

public void run () {
	Initializer in;

	while ( true ) {
		synchronized ( lock ) {
			if ( prodQueue != null ) {
				in = prodQueue;
				prodQueue = prodQueue.next;
			}
			else
				break;
		}

		in.startProduction();
	}
	
	synchronized ( lock ) {
		prodThread = null;
	}
}

public void setColorModel ( ColorModel clrModel ) {
}

public void setDimensions ( int w, int h ){
	if ( (w != width) || (h != height) ) {
		width = w;
		height = h;

		// we need a target for subsequent setPixel() calls
		nativeData = Toolkit.imgCreateImage( width, height);
		stateChange( flags | ImageObserver.WIDTH | ImageObserver.HEIGHT, 0, 0, width, height);
	}
}

public void setHints( int hints) {
}

public void setPixels( int x, int y, int w, int h, ColorModel model, byte[] pels, int off, int scans) {
	if ( nativeData == null ) {
		// error, we did not get a setDimension call
		return;
	}

	if ( model instanceof IndexColorModel ) {
		IndexColorModel icm = (IndexColorModel) model;
		Toolkit.imgSetIdxPels( nativeData, x, y, w, h, icm.rgbs, pels,
		                       icm.trans, off, scans);
		stateChange( flags | ImageObserver.SOMEBITS, x, y, w, h);
	}
}

public void setPixels ( int x, int y, int w, int h,
                 ColorModel model, int[] pels, int off, int scans) {
	if ( nativeData == null ) {
		// error, we did not get a setDimension call
		return;
	}

	if ( model instanceof DirectColorModel ) {
		if ( model != ColorModel.getRGBdefault() ){
			for ( int i=x; i<scans; i++ )
				pels[i] = model.getRGB( pels[i]);
		}
		
		Toolkit.imgSetRGBPels( nativeData, x, y, w, h, pels, off, scans);
		stateChange( flags | ImageObserver.SOMEBITS, x, y, w, h);
	}
}

public void setProperties ( Hashtable props ) {
  stateChange( flags | ImageObserver.PROPERTIES, 0, 0, width, height);
}

void startProduction () {
	flags |= PRODUCING;

	if ( producer != null )
		producer.startProduction( this);
	else if ( srcObj != null ) {
		if ( srcObj instanceof File )
			createFromFile();
		else if ( srcObj instanceof URL )
			createFromURL();
	}
}
}

Image ( File file ) {
	srcObj = file;
	createFromFile();
}

Image ( Image img, int w, int h ) {
	nativeData = Toolkit.imgCreateScaledImage( img.nativeData, w, h );
	
	width = w;
	height = h;

	flags = READY;
}

Image ( ImageProducer ip ) {
	producer = ip;
}

Image ( URL url ) {
	srcObj = url;
}

Image ( byte[] data, int offs, int len) {
	TmpFile  tmp = null;
	int   i = offs;

	// check data-headers to identify format
	if ( (data[i++] == 'G') && (data[i++] == 'I') && (data[i] == 'F') ) { // GIF
		tmp = new TmpFile( null, ".gif");
	}
	else if ( (data[i++] == 0xff) && (data[i++] == 0xd8) &&
	          (data[i++] == 0xff) && (data[i] == 0xe0) ) {                // JPEG
	  tmp = new TmpFile( null, ".jpeg");
	}

	if ( tmp != null ) {
		try {
			FileOutputStream out = new FileOutputStream( tmp);
			out.write( data, offs, len);
			out.close();
			srcObj = tmp;
			createFromFile();
			tmp.delete();
		}
		catch ( Exception x ) {}
	}
}

Image ( int w, int h ) {
	nativeData = Toolkit.imgCreateScreenImage( w, h );
	
	width = w;
	height = h;

	flags = READY | SCREEN;
}

void addObserver ( ImageObserver observer ) {
	if ( observer != null ) {
		if ( observers == null ) {
			observers = new Vector( 2);
			observers.addElement( observer);
		}
		else {
			if ( !observers.contains( observer) )
				observers.addElement( observer);
		}
	}
}

static int checkImage ( Image image, int width, int height, ImageObserver obs, boolean load ){
	if ( image == null )
		return 0;

	if ( (image.flags & ImageObserver.ALLBITS) != 0 ) {
		if ( (width > 0) && (height > 0) ){
			image.scale( width, height);
		}
	}
	else {
		if ( load ) {
			image.addObserver( obs);
			if ( (image.flags & PRODUCING) == 0 ) {
				image.startAsyncProduction();
			}
		}
	}	
	return image.flags;
}

void createFromFile () {
	File file = (File)srcObj;
	String fileName = file.getAbsolutePath();

	if ( file.exists() ) {
		if ( fileName.endsWith( ".gif") || fileName.endsWith( ".GIF") ){
			flags |= PRODUCING;
			nativeData = Toolkit.imgCreateGifImage( fileName);
		}
		else if ( fileName.endsWith( ".jpg") || fileName.endsWith( ".jpeg") ||
		          fileName.endsWith( ".JPG") || fileName.endsWith( ".JPEG") ){
			flags |= PRODUCING;
			nativeData = Toolkit.imgCreateJpegImage( fileName, 256);
		}
	}

	flags = READY;
	
	if ( nativeData != null ) {
		width = Toolkit.imgGetWidth( nativeData);
		height = Toolkit.imgGetHeight( nativeData);

		stateChange( ImageObserver.ALLBITS | ImageObserver.WIDTH | ImageObserver.HEIGHT,
		             0, 0, width, height);
	}
	else {
		stateChange( ImageObserver.ERROR | ImageObserver.ABORT, 0, 0, width, height);
	}
}

void createFromURL () {
	URL url = (URL)srcObj;
	// how to deal with the SecurityManager ??
	TmpFile tmp = new TmpFile( url.getFile());
	byte[] buf = new byte[1024];
	int n;
	
	try {
		InputStream in = url.openStream();
		FileOutputStream out = new FileOutputStream( tmp);
		
		while ( (n = in.read( buf)) >= 0 ) {
			out.write( buf, 0, n);
		}
	
		out.close();
		in.close();
	
		srcObj = tmp;
		createFromFile();
		tmp.delete();
	}
	catch ( IOException x ) {
	}
}

protected void finalize () {
	flush();
}

public void flush () {
	if ( nativeData != null ){
		Toolkit.imgFreeImage( nativeData);
		nativeData = null;
	}
}

public Graphics getGraphics () {
	if ( ((flags & SCREEN) == 0) || (nativeData == null) )
		return null;
	else
		return Graphics.getGraphics( this, 0, 0, 0, 0, width, height,
	 	                             Color.black, Color.white, Defaults.WndFont, false);
}

public int getHeight ( ImageObserver observer ) {
	if ( (flags & ImageObserver.HEIGHT) != 0 )
		return height;
	else {
		addObserver( observer);
		if ( (flags & PRODUCING) == 0 )
			startAsyncProduction();

		return -1;
	}
}

public Object getProperty ( String name, ImageObserver observer ) {
	return null;
}

public Image getScaledInstance ( int width, int height, int hints ) {
	if ( (nativeData == null) || (width <= 0) || (height <= 0) ||
	     ((flags & SCREEN) != 0) || ((flags & ImageObserver.ALLBITS) == 0) )
		return null;

	return new Image( this, width, height);
}

public ImageProducer getSource () {
	if ( nativeData != null )
		return new NativeProducer();
	else
		return producer;
}

public int getWidth ( ImageObserver observer ) {
	if ( (flags & ImageObserver.WIDTH) != 0 )
		return width;
	else {
		addObserver( observer);
		if ( (flags & PRODUCING) == 0 )
			startAsyncProduction();

		return -1;
	}
}

void removeObserver ( ImageObserver observer ) {
	if ( observers != null ) {
		if ( observers.size() == 1 )
			observers = null;
		else
			observers.removeElement( observer);
	}
}

boolean scale ( int w, int h ) {
	if ( ((flags & SCREEN) != 0) || ((flags & ImageObserver.ALLBITS) == 0) || (nativeData == null) )
		return false;

	if ( (w != width) || (h != height) ) {
		Ptr oldNativeData = nativeData;
		nativeData = Toolkit.imgCreateScaledImage( nativeData, w, h );
		Toolkit.imgFreeImage( oldNativeData);
	
		width = w;
		height = h;
	}

	return true;
}

synchronized void startAsyncProduction () {
	Initializer p;
	Initializer in = new Initializer();

	flags |= PRODUCING;

	synchronized ( lock ) {
		if ( prodQueue == null )
			prodQueue = in;
		else {
			for ( p=prodQueue; p.next != null; p = p.next );
			p.next = in;
		}
	}

	if ( prodThread == null ) {
		prodThread = new Thread( prodQueue );
		prodThread.setPriority( Thread.MIN_PRIORITY + 1);
		prodThread.start();
	}
}

void stateChange( int flags, int x, int y, int w, int h) {
	this.flags = flags;

	if ( observers != null ) {
	 	for ( Enumeration e = observers.elements(); e.hasMoreElements(); ) {
			ImageObserver obs = (ImageObserver) e.nextElement();
			if ( !obs.imageUpdate( this, flags, x, y, w, h))
				observers.removeElement( obs);
		}
	}
}

public String toString() {
	return getClass().getName() + " [" + width + ',' + height + ", flags: "  + flags + ']';
}
}
