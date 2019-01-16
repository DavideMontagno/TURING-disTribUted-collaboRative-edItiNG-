import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class UserOnline {

	static HashMap <String, String> user_online = null;
	Lock lock;
	public UserOnline() {
		this.user_online = new HashMap<String,String>();
		this.lock = new ReentrantLock();
	}
	
	public boolean get(String username) {
		lock.lock();
		
		for (Map.Entry<String, String> entry : user_online.entrySet())
		{	
		
			if(entry.getKey().equals(username)) {
				lock.unlock();
				return true;
			}
		}
		
			lock.unlock();
			return false;
		
		
	}

	public void put(String username, String hostAddress) {
		lock.lock();
		user_online.put(username, hostAddress);
		lock.unlock();
		
	}

	public void remove(String username, String hostAddress) {
		lock.lock();
		for (Map.Entry<String, String> entry : user_online.entrySet())
		{	
			
			if(entry.getKey().equals(username)) {
				user_online.remove(entry.getKey());
				break;
			}
		}
		lock.unlock();
		
	}
	
	public int getSize() {
		int value=0;
		lock.lock();
		value = user_online.size();
		lock.unlock();
		return value;
	}

	public boolean containsKey(String username) {
		boolean check=false;
		lock.lock();
		check = user_online.containsKey(username);
		lock.unlock();
		return check;
	}
	
	public String toString() {
		String to_return;
		lock.lock();
		to_return = user_online.toString();
		lock.unlock();
		return to_return;
	}

	public String getAddress(String username) {
		String to_return = null;
		lock.lock();
		for (Map.Entry<String, String> entry : user_online.entrySet())
		{	
		
			
			if(entry.getKey().equals(username)) {
				to_return = entry.getValue();
				break;
			}
		}
		lock.unlock();
		return to_return;
	}


	
	
}
