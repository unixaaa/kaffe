/*
 * java.lang.ClassLoader.c
 *
 * Copyright (c) 1996, 1997
 *	Transvirtual Technologies, Inc.  All rights reserved.
 *
 * See the file "license.terms" for information on usage and redistribution 
 * of this file. 
 */

#include "config.h"
#include "config-std.h"
#include "config-mem.h"
#include "../../../kaffe/kaffevm/gtypes.h"
#include "../../../kaffe/kaffevm/file.h"
#include "../../../kaffe/kaffevm/readClass.h"
#include "../../../kaffe/kaffevm/constants.h"
#include "../../../kaffe/kaffevm/access.h"
#include "../../../kaffe/kaffevm/classMethod.h"
#include "../../../kaffe/kaffevm/object.h"
#include "../../../kaffe/kaffevm/locks.h"
#include "../../../kaffe/kaffevm/itypes.h"
#include "../../../kaffe/kaffevm/support.h"
#include "../../../kaffe/kaffevm/baseClasses.h"
#include "../../../kaffe/kaffevm/exception.h"
#include <native.h>
#include "defs.h"

/*
 * Translate an array of bytes into a class.
 */
struct Hjava_lang_Class*
java_lang_ClassLoader_defineClass0(struct Hjava_lang_ClassLoader* this, struct Hjava_lang_String* name, HArrayOfByte* data, jint offset, jint length)
{
	Hjava_lang_Class* clazz;
	classFile hand;
	classEntry *centry;
	errorInfo info;

	hand.base = &unhand(data)->body[offset];
	hand.buf = hand.base;
	hand.size = length;

	clazz = newClass();
	/*
	 * Make sure clazz->centry is NULL here, so that nobody will try to
	 * assert that a lock on centry is held during readClass
	 */
	clazz->centry = NULL;
	clazz = readClass(clazz, &hand, this, &info);
	if (clazz == 0)
		throwError(&info);

	/* 
	 * If a name was given, but the name we found in the class file
	 * was a different one, complain.
	 */
	if (name != NULL) {
		/* The name uses dots, but clazz->name uses slashes */
		Hjava_lang_String *temp = makeReplaceJavaStringFromUtf8(
			clazz->name->data, clazz->name->length, '/', '.'); 

		if (STRING_SIZE(temp) != STRING_SIZE(name) ||
			memcmp(STRING_DATA(temp), STRING_DATA(name), 
				STRING_SIZE(temp)) != 0)
			SignalError("java.lang.ClassFormatError", "Wrong name");
	}

	/*
	 * See if an entry for that name and class loader already exists
	 * create one if not.
	 */
	centry = lookupClassEntry(clazz->name, this);
	assert(centry != 0);

	/*
	 * see if somebody loaded that class already
	 */
	lockMutex(centry);
	if (centry->class != NULL) {
		unlockMutex(centry);
		SignalError("java.lang.ClassFormatError", "Duplicate name");
	}

	/* enter the class we loaded and return */
	centry->class = clazz;
	clazz->centry = centry;
	unlockMutex(centry);

	/*
	 * While it is not necessary that one be able to actually *use*
	 * the returned class object at this point, it is mandatory that
	 * the returned clazz object is a functional Class object.
	 *
	 * The following call will make sure that the returned class object
	 * has its dispatch table set.  The transition PRELOADED->PREPARED 
	 * in processClass sets class->head.dtable.
	 *
	 * Presumably, it shouldn't be necessary here, but is at the
	 * moment - XXX
	 */
	if (processClass(clazz, CSTATE_PREPARED, &info) == false) {
		throwError(&info);
	}
	return (clazz);
}

/*
 * Resolve classes reference by this class.
 */
void
java_lang_ClassLoader_resolveClass0(struct Hjava_lang_ClassLoader* this, struct Hjava_lang_Class* class)
{
	errorInfo info;
	if (processClass(class, CSTATE_LINKED, &info) == false) {
		throwError(&info);
	}
}

/*
 * Load a system class.
 */
struct Hjava_lang_Class*
java_lang_ClassLoader_findSystemClass0(Hjava_lang_ClassLoader* this, Hjava_lang_String* str)
{
	errorInfo info;
	Hjava_lang_Class *clazz;
	int len = javaStringLength(str);
	Utf8Const* c;
	char* name;
#if INTERN_UTF8CONSTS
	char buffer[100];
	if (len <= 100) {
		name = buffer;
	}
	else {
		name = KMALLOC (len);
	}
#else
	c = KMALLOC(sizeof(Utf8Const) + len + 1);
	name = c->data;
#endif
        javaString2CString(str, name, len+1);
	classname2pathname (name, name);
#if INTERN_UTF8CONSTS
	c = makeUtf8Const (name, len);
	if (name != buffer) {
		KFREE(name);
	}
#else /* ! INTERN_UTF8CONSTS */
	c->length = len;
	c->hash = (uint16) hashUtf8String (name, len);
#endif /* ! INTERN_UTF8CONSTS */
	clazz = loadClass(c, 0, &info);
	if (clazz == 0) {
		/* 
		 * upgrade error to an exception if *this* class wasn't found.
		 * See discussion in Class.forName()
		 */
		if (!strcmp(info.classname, "java.lang.NoClassDefFoundError")
		    && !strcmp(info.mess, name))
		{
			SET_LANG_EXCEPTION_MESSAGE(&info,
				ClassNotFoundException, info.mess)
		}
		throwError(&info);
	}

	if (processClass(clazz, CSTATE_COMPLETE, &info) == false) {
		throwError(&info);
	}
	return (clazz);
}

/*
 * Locate the requested resource in the current Jar files and create a
 *  byte array containing it.
 */
HArrayOfByte*
java_lang_ClassLoader_getSystemResourceAsBytes0(struct Hjava_lang_String* str)
{
	char* name;
	classFile hand;
	HArrayOfByte* data;
	errorInfo err;

	name = makeCString(str);
	hand = findInJar(name, &err);
	KFREE(name);
	if (hand.type == 0) {
		return (NULL);
	}

	/* Copy data from returned buffer into Java byte array.  Be nice
	 * to avoid this copy but we cannot for the moment.
	 */
	data = (HArrayOfByte*)AllocArray(hand.size, TYPE_Byte);
	memcpy(unhand(data)->body, hand.buf, hand.size);
	if (hand.base != NULL) {
		KFREE(hand.base);
	}

	return (data);
}

/*
 * Find a loaded class.
 */
struct Hjava_lang_Class*
java_lang_ClassLoader_findLoadedClass0(Hjava_lang_ClassLoader* this, Hjava_lang_String* str)
{
        int len = javaStringLength(str);
        Utf8Const* c;
        char* name;
        char buffer[100];
        classEntry* entry;

        if (len <= 100) {
                name = buffer;
        }
        else {
                name = KMALLOC (len);
        }
        javaString2CString(str, name, len+1);
        classname2pathname (name, name);
        c = makeUtf8Const (name, len);
        if (name != buffer) {
                KFREE(name);
        }

        entry = lookupClassEntryInternal(c, this);
	if (entry != 0) {
		return (entry->class);
	} else {
		return (0);
	}
}
