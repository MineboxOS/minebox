/* Released under GPL 2.0
 * (C) 2011 by folkert@vanheusden.com
 */
package com.vanheusden.BlackHole.cache;

import com.vanheusden.BlackHole.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UniqueOriginalOrderList<K, V> {
	static String version = "$Id: UniqueOriginalOrderList.java 606 2013-07-06 22:07:22Z folkert $";
	// http://www.artima.com/weblogs/viewpost.jsp?thread=122295
	protected final LinkedHashMap<K, V> map = new LinkedHashMap<K, V>();
	protected Dummy myMonitorObjectPut = new Dummy();
	protected Dummy myMonitorObjectGet = new Dummy();

	/* add a key/value pair
	 * if it's already in the map, update the value but don't add to list
	 * if it is not in the map, add to map and list
	 */
	public void put(K key, V value) {
		map.put(key, value);

		doNotifyAPut();
	}

	public Set<K> keySet() {
		return map.keySet();
	}

	public boolean remove(K key) {
		V old = map.remove(key);
		return old != null;
	}

        public void doWaitForGet() throws InterruptedException {
                synchronized(myMonitorObjectGet) {
                        myMonitorObjectGet.wait();
                }
        }

        private void doNotifyAGet() {
                synchronized(myMonitorObjectGet){
                        myMonitorObjectGet.notifyAll();
                }
        }

        private void doWaitForPut() throws InterruptedException {
                synchronized(myMonitorObjectPut) {
                        myMonitorObjectPut.wait();
                }
        }

        private void doWaitForPut(int maxSleep) throws InterruptedException {
                synchronized(myMonitorObjectPut) {
                        myMonitorObjectPut.wait(maxSleep);
                }
        }

        private void doNotifyAPut() {
                synchronized(myMonitorObjectPut){
                        myMonitorObjectPut.notifyAll();
                }
        }

	/* wait until there's an object in the list/map
	 * then return it
	 */
	public void waitForElement() throws InterruptedException {
		doWaitForPut();
	}
	public void waitForElement(int maxSleep) throws InterruptedException {
		doWaitForPut(maxSleep);
	}

	public int size() {
		return map.size();
	}

	/* get first element from list and then remove it
	 * from the list
	 */
	public Map.Entry<K, V> getFromBottomAndRemove() {
		Iterator<Map.Entry<K, V>> it = map.entrySet().iterator();
		assert it.hasNext();
		Map.Entry<K, V> pair = it.next();
		it.remove();

		doNotifyAGet();

		return pair;
	}

	public List<Map.Entry<K, V>> getFromBottomAndRemove(int nToGet) {
		List<Map.Entry<K, V>> list = new ArrayList<Map.Entry<K, V>>(nToGet);

		Iterator<Map.Entry<K, V>> it = map.entrySet().iterator();
		for(int loop=0; loop<nToGet; loop++) {
			assert it.hasNext();
			list.add(it.next());
			it.remove();
		}

		doNotifyAGet();

		return list;
	}

	public V get(K key) {
		return map.get(key);
	}
}
