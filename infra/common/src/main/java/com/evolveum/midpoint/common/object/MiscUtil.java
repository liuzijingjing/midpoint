/**
 * Copyright (c) 2011 Evolveum
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1 or
 * CDDLv1.0.txt file in the source code distribution.
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted 2011 [name of copyright owner]"
 * 
 */
package com.evolveum.midpoint.common.object;

import java.util.ArrayList;
import java.util.List;

import com.evolveum.midpoint.xml.ns._public.common.common_1.ObjectListType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ObjectType;

/**
 * @author Radovan Semancik
 *
 */
public class MiscUtil {
	
	public static ObjectListType toObjectListType(List<? extends ObjectType> list) {
		ObjectListType listType = new ObjectListType();
		for (ObjectType o : list) {
			listType.getObject().add(o);
		}
		return listType;
	}
	
	public static <T extends ObjectType> List<T> toList(Class<T> type, ObjectListType listType) {
		List<T> list = new ArrayList<T>();
		for (ObjectType o : listType.getObject()) {
			list.add((T)o);
		}
		return list;
	}

}
