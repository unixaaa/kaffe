package java.awt;

import java.awt.event.KeyEvent;
import java.util.Vector;

/**
 * class Menu -
 *
 * Copyright (c) 1998
 *      Transvirtual Technologies, Inc.  All rights reserved.
 *
 * See the file "license.terms" for information on usage and redistribution
 * of this file.
 *
 * @author J.Mehlitz
 */
public class Menu
  extends MenuItem
  implements MenuContainer
{
	Vector items = new Vector( 5);
	boolean isTearOff;

public Menu() {
	this( null, false);
}

public Menu( String label) {
	this( label, false);
}

public Menu( String label, boolean tearOff) {
	super( label);
	isTearOff = tearOff;
}

public synchronized MenuItem add( MenuItem mi) {
	insert( mi, -1);
	return mi;
}

public void add( String label) {
	insert( label, -1);
}

public void addNotify() {
}

public void addSeparator() {
	insertSeparator( -1);
}

Vector addShortcuts ( Vector v) {
	int sz = items.size();

	super.addShortcuts( v);
	for ( int i=0; i<sz; i++)
		((MenuItem)items.elementAt( i)).addShortcuts( v);

	return v;	
}

public MenuItem getItem( int idx) {
	try { return (MenuItem)items.elementAt( idx); }
	catch( Exception e) { return null; }
}

public int getItemCount() {
	try { return items.size(); }
	catch( Exception e) { return 0; }
}

MenuItem getShortcutMenuItem( MenuShortcut s) {
	int sz = items.size();

	MenuItem mi = super.getShortcutMenuItem(s);
	if ( mi != null)
		return mi;

	for ( int i=0; i<sz; i++) {
		mi = ((MenuItem)items.elementAt( i)).getShortcutMenuItem( s);
		if ( mi != null )
			return mi;
	}
	return null;
			
}

boolean handleShortcut ( KeyEvent e) {
	int s = items.size();
	for ( int i=0; i<s; i++){
		MenuItem mi = (MenuItem)items.elementAt( i);
		if ( mi.handleShortcut( e) )
			return true;
	}
	return false;
}

public synchronized void insert( MenuItem mi, int idx) {
	try { 
		items.insertElementAt( mi, idx > -1 ? idx : items.size());
		mi.parent = this;
	}
	catch( Exception e) {}
}

public void insert( String label, int idx) {
	insert( new MenuItem( label), idx);
}

public void insertSeparator( int idx) {
	insert( MenuItem.separator, idx);
}

public boolean isTearOff() {
	return isTearOff;
}

public String paramString() {
	return super.paramString();
}

public synchronized void remove( MenuComponent m) {
	try { items.removeElement( m); }
	catch( Exception e) {}
}

public synchronized void remove( int idx) {
	try { items.removeElementAt( idx); }
	catch( Exception e) {}
}

public synchronized void removeAll() {
	try { items.removeAllElements(); }
	catch( Exception e) {}
}

public void removeNotify() {
}
}
