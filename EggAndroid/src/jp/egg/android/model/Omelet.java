package jp.egg.android.model;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jp.egg.android.db.annotation.Egg;
import jp.egg.android.db.model.Model;
import android.util.Log;



public class Omelet {




	public static boolean isInheritanceClassOf(Class<?> type, Class<?> superClass){
		return superClass.equals(type) || isSubclassOf(type, superClass);
	}

	public static boolean isSubclassOf(Class<?> type, Class<?> superClass) {
		if (type.getSuperclass() != null) {
			if (type.getSuperclass().equals(superClass)) {
				return true;
			}

			return isSubclassOf(type.getSuperclass(), superClass);
		}

		return false;
	}


	public static Set<Field> getEggDeclaredFields(Class<?> type) {
		Set<Field> declaredColumnFields = Collections.emptySet();

		if (true) {
			declaredColumnFields = new LinkedHashSet<Field>();

			Field[] fields = type.getDeclaredFields();
			Arrays.sort(fields, new Comparator<Field>() {
				@Override
				public int compare(Field field1, Field field2) {
					return field2.getName().compareTo(field1.getName());
				}
			});
			for (Field field : fields) {
				Log.d("test", "name = "+field.getName()+";");
				if (field.isAnnotationPresent(Egg.class)) {
					declaredColumnFields.add(field);
				}
			}

			Class<?> parentType = type.getSuperclass();
			if (parentType != null) {
				declaredColumnFields.addAll(getEggDeclaredFields(parentType));
			}

			for (Field field : fields) {
				declaredColumnFields.addAll(getEggDeclaredFields(field.getType()));
			}
		}

		return declaredColumnFields;
	}

	public static final void saveEggAll(Object obj){
		Set<Object> list = listEggAnotationFieldObject(obj);
		Log.d("Omelet", "saveEggAll number is "+list.size()+".");
		for(Object e : list){
			if( e instanceof Model ){
				Model model = (Model)e;
				Log.d("Omelet", ""+e.getClass().getSimpleName()+".save() on saveEggAll.");
				model.save();
			}
			else if( e.getClass().isArray() ){
				 int num = Array.getLength(e);
				 for(int i=0;i<num;i++){
					Object a = Array.get(e, i);
					if( a instanceof Model){
						((Model) a).save();
					}
				 }
			}
			else if( e instanceof List ){
				List<?> l = (List<?>)e;
				int num = l.size();
				 for(int i=0;i<num;i++){
					Object a = l.get(i);
					if( a instanceof Model){
						((Model) a).save();
					}
				 }
			}

		}
	}

	public static final Set<Object> listEggAnotationFieldObject(Object obj){
		Set<Object> list = new LinkedHashSet<Object>();
		if(obj == null) return list;

		Set<Field> fields = getEggDeclaredFields(obj.getClass());
		for(Field e : fields){
			Object field_value;
			try {
				e.setAccessible(true);
				field_value = e.get(obj); //TODO 保有オブジェクトを！！
			} catch (Exception e1) {
				e1.printStackTrace();
				field_value = null;
			}

			if(field_value!=null){
				if(e.isAnnotationPresent(Egg.class)){
					list.add(field_value);
				}
				list.addAll( listEggAnotationFieldObject(field_value) );
			}
		}

		return list;
	}

}