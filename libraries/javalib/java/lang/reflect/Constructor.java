package java.lang.reflect;


/*
 * Java core library component.
 *
 * Copyright (c) 1997, 1998
 *      Transvirtual Technologies, Inc.  All rights reserved.
 *
 * See the file "license.terms" for information on usage and redistribution
 * of this file.
 */
public class Constructor
  implements Member
{
	private Class clazz;
	private int slot;
	private Class[] parameterTypes;
	private Class[] exceptiontypes;

public boolean equals(Object obj)
	{
	// Catch the simple case wher they're really the same
	if ((Object)this == obj) {
		return (true);
	}

	Constructor cobj;
	try {
		cobj = (Constructor)obj;
	}
	catch (ClassCastException _) {
		return (false);
	}

	if (clazz != cobj.clazz) {
		return (false);
	}
	if (parameterTypes.length != cobj.parameterTypes.length) {
		return (false);
	}   
	for (int i = 0; i < parameterTypes.length; i++) {
		if (parameterTypes[i] != cobj.parameterTypes[i]) {
			return (false);
		}
	}
	return (true);
}

public Class getDeclaringClass()
	{
	return (clazz);
}

public Class[] getExceptionTypes()
	{
	return (exceptiontypes);
}

native public int getModifiers();

public String getName()
	{
	return (clazz.getName());
}

public Class[] getParameterTypes()
	{
	return (parameterTypes);
}

public int hashCode()
	{
	return (clazz.hashCode());
}

native public Object newInstance(Object initargs[]) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException;

public String toString()
	{
	StringBuffer str = new StringBuffer();;
	int mod = getModifiers();

	// Modifier
	if ((mod & 1) == 1) {
		str.append("public ");
	}
	else if ((mod & 2) == 2) {
		str.append("private ");
	}
	else if ((mod & 4) == 4) {
		str.append("protected ");
	}

	// Class name.
	str.append(clazz.getName());
	str.append("(");

	// Signature
	for (int i = 0; i < parameterTypes.length; i++) {
		str.append(parameterTypes[i].getName());
		if (i+1 < parameterTypes.length) {
			str.append(",");
		}
	}
	str.append(")");

	return (new String(str));
}
}
