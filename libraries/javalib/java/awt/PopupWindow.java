package java.awt;

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.Vector;

/**
 * class PopupWindow -
 *
 * Copyright (c) 1998
 *      Transvirtual Technologies, Inc.  All rights reserved.
 *
 * See the file "license.terms" for information on usage and redistribution
 * of this file.
 *
 * @author J.Mehlitz
 */
class PopupWindow
  extends Window
{
	MenuItem selection;
	static int xOffs = 8;
	Vector items;
	Component client;
	PopupWindow sub;
	static boolean disposeOnLost;

class PopupListener
  implements MouseListener, MouseMotionListener, KeyListener, FocusListener
{
public PopupListener () {
	addMouseListener( this);
	addMouseMotionListener( this);
	addKeyListener( this);
	addFocusListener( this);
}

public void focusGained ( FocusEvent evt ) {
}

public void focusLost ( FocusEvent evt ) {
	if ( disposeOnLost)
		disposeAll();
}

public void keyPressed ( KeyEvent evt ) {
	int cc = evt.getKeyCode();
	switch( cc) {
		case KeyEvent.VK_ENTER:
			processSelection();
			break;
		case KeyEvent.VK_UP:
			selectPrev();
			break;
		case KeyEvent.VK_DOWN:
			selectNext();
			break;
		case KeyEvent.VK_ESCAPE:
			disposeAll();
			break;
		case KeyEvent.VK_LEFT:
			selectUpper();
			break;
		case KeyEvent.VK_RIGHT:
			selectLower();
			break;
	}
}

public void keyReleased ( KeyEvent evt ) {
}

public void keyTyped ( KeyEvent evt ) {
}

public void mouseClicked ( MouseEvent evt ) {
	processSelection();
}

public void mouseDragged ( MouseEvent evt ) {
}

public void mouseEntered ( MouseEvent evt ) {
	disposeOnLost = false;
	requestFocus();
}

public void mouseExited ( MouseEvent evt ) {
	disposeOnLost = true;
}

public void mouseMoved ( MouseEvent evt ) {
	selectItem( itemAt( evt.getY() ));
}

public void mousePressed ( MouseEvent evt ) {
}

public void mouseReleased ( MouseEvent evt ) {
}
}

public PopupWindow ( Component client, Frame fr, int x, int y, Vector items) {
	super( fr);
	this.client = client;
	this.items  = items;
	
	Dimension wd = Toolkit.singleton.getScreenSize();
	Dimension md = getPreferredSize();

	if ( x + md.width > wd.width)
		x = wd.width - md.width;

	if ( y + md.height > wd.height)
		y = wd.height - md.height;

	setBounds( x, y, md.width, md.height);
	new PopupListener();

	if ( !(client instanceof PopupWindow) )
		AWTEvent.setPopup( this);

	setVisible( true);
}

public void dispose () {

	if ( !(client instanceof PopupWindow) )
		AWTEvent.resetPopup( this);
		
	super.dispose();
}

void disposeAll() {
	PopupWindow p = rootWnd();
	p.disposeSubMenus();
	p.dispose();
	p.client.requestFocus();
}

void disposeSubMenus() {
	for ( PopupWindow p = sub; p != null; p = p.sub)
		p.dispose();
	sub = null;
}

public Dimension getPreferredSize() {
	int s = (items != null) ? items.size() : 0;
	int th = 0;
	int tw = 0;
	int cs = 2;
	
	for ( int i=0; i<s; i++) {
		MenuItem mi = (MenuItem)items.elementAt( i);
		if ( mi instanceof Menu)
			cs = 3;
		tw = Math.max( tw, mi.getWidth() );
		th += mi.getHeight();
	}

	return new Dimension( tw+cs*xOffs +4, th +4);  // border width
}

int getY ( MenuItem mi) {
	int s = items.size();
	int y = 2;
	
	for ( int i=0; i<s; i++) {
		MenuItem cmi = (MenuItem)items.elementAt( i);
		if ( cmi == mi )
			return y;
		y += cmi.getHeight();
	}

	return -1;
}

MenuItem itemAt ( int yPos) {
	int s = items.size();
	int y = 0;
	
	for ( int i=0; i<s; i++) {
		MenuItem mi = (MenuItem)items.elementAt( i);
		y += mi.getHeight();
		if ( y > yPos)
			return mi;
	}

	return null;
}

public void notifyLost( Component newFocus) {
	if ( !( newFocus instanceof PopupWindow))
		disposeAll();
}

boolean openSubMenu () {
	if ( ( selection == null) || ( ! (selection instanceof Menu) ) )
		return false;

	Vector v = ((Menu)selection).items;
	if ( (v == null) || (v.size() == 0) )
		return false;
		
	sub = new PopupWindow( this, owner, x+width, y+getY( selection), v );
	return true;
}

public void paint( Graphics g) {
	int s = items.size();
	int x = xOffs;
	int y = 2;   // border width

	g.setColor( Color.black);
	g.drawRect( 0, 0, width-1, height-1);
	g.setColor( bgClr);		
	g.fill3DRect( 1, 1, width-2, height-2, true);
	for ( int i=0; i<s; i++) {
		MenuItem mi = (MenuItem)items.elementAt( i);
		y += paintItem( mi, g, y);
	}

}

int paintItem ( MenuItem mi, Graphics g, int y) {
	int ih = mi.paint( g, xOffs, y +2, width, bgClr, fgClr, mi == selection);

	// draw the submenu mark
	if ( mi instanceof Menu) {
		int my = y + ih/2;
		int mx = width - 4;
		for ( int i=0; i<4; i++)
			g.drawLine( mx-2*i, my-i, mx-2*i, my+i);
	}

	return ih;
}

void processSelection () {
	if ( selection == null)
		return;
	if ( selection instanceof Menu){
		disposeSubMenus();
		openSubMenu();
	}
	else {
		disposeAll(); // clean up before doing any popup triggered actions
		selection.process();
	}
}

PopupWindow rootWnd () {
	for ( Component c = this; c != null; ) {
		if ( c instanceof PopupWindow){
			PopupWindow pc = (PopupWindow) c;
			if ( ! (pc.client instanceof PopupWindow ) )
				return pc;
			c = pc.client;
		}
	}
	return null;
}

boolean selectItem ( MenuItem nmi) {
	if ( (nmi == null) || (nmi.isSeparator()) || (nmi == selection) )
		return false;
		
	Graphics g = getGraphics();

	if ( selection != null){
		MenuItem cmi = selection;
		selection = null;
		paintItem( cmi, g, getY( cmi) );
	}
	
	selection = nmi;
	paintItem( selection, g, getY( selection) );
		
	g.dispose();
	return true;	
}

void selectLower() {
	if ( sub != null) {
		sub.requestFocus();
		Vector v = sub.items;
		if ( (v != null) && (v.size() > 0) )
			sub.selectItem ((MenuItem) v.firstElement());
	}
}

void selectNext () {
	try {
		if ( selection == null)
			selectItem( (MenuItem)items.firstElement() );
		else {
			for ( int idx = items.indexOf( selection)+1; idx<items.size(); idx++) {
				MenuItem mi = (MenuItem)items.elementAt( idx);
				if ( ! mi.isSeparator() ){
					selectItem( mi);
					return;
				}
			}
		}
	}
	catch ( Exception e) {}
}

void selectPrev () {
	try {
		if ( selection == null)
			selectItem( (MenuItem)items.lastElement() );
		else {
			for ( int idx = items.indexOf( selection)-1; idx>=0; idx--) {
				MenuItem mi = (MenuItem)items.elementAt( idx);
				if ( ! mi.isSeparator() ){
					selectItem( mi);
					return;
				}
			}
		}
	}
	catch ( Exception e) {}
}

void selectUpper() {
	if ( client instanceof PopupWindow) {
		PopupWindow p = (PopupWindow)client;
		dispose();
		p.requestFocus();
		p.sub = null;
	}
}
}
