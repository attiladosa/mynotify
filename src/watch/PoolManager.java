package watch;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/* To Do
 * 1. Change in mem structures to database storage
 */
public class PoolManager {
	/*****
	 * pools -list of pools containing `poolSize` threads each watchers -list of
	 * inotify instances. Each pool is given 1 instance keys -each pool (every
	 * thread in it) needs a hashmap of keys that represent a file under
	 * subscription and the name of the file itself. A list of these hashmaps is
	 * needed. One for each pool file_subscriptions -map of file_name and the
	 * inotify instance that it is mapped to.
	 * 
	 * These data structures are redundant. But they store only references. So
	 * that is acceptable since it brings in convenience while programming.
	 */
	ArrayList<ExecutorService> pools;
	ArrayList<WatchService> watchers;
	ArrayList<ConcurrentHashMap<WatchKey, Path>> keys;
	HashMap<String, WatchService> file_subscriptions;

	final int threadsPerPool = 1;
	int poolSize;
	/* bootstraps all the data structures */
	PoolManager(int poolSize) throws IOException {
		this.poolSize = poolSize;
		file_subscriptions = new HashMap<String, WatchService>();
		initializeThreadPools();
	}

	/* watch a file */
	public void watch(String path, boolean recursive_watch) throws IOException {

		if (other_subscribers_subscribe_to(path)) {
			// some thread is already writing into an exchange for the file
			// being subscribed to
		} else {
			WatchService ws = registerFileToRandomPool(path);

			file_subscriptions.put(path, ws);
		}
	}

	/*
	 * pick a random pool to handle the subscription and subscribe to the
	 * inotify instance that the pool is notified by.
	 */
	private WatchService registerFileToRandomPool(String path)
			throws IOException {

		Random randomGenerator = new Random();
		int i = randomGenerator.nextInt(this.poolSize);
		int index =0;
		for (index = 0; index < this.poolSize ; index++) {

			WatchService watcher = watchers.get(index);
			ConcurrentHashMap<WatchKey, Path> keySet = keys.get(index);

			Path dir = Paths.get(path);

			WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE,
					ENTRY_MODIFY);

			/**
			 * Check if the current registration is an update or a new
			 * registration. This is a reundant path. Will not be reached in the
			 * current scheme of things
			 */
			Path prev = keySet.get(key);

			if (prev == null) {
				System.out.format("register: %s\n", dir);
			} else {
				if (!dir.equals(prev)) {
					System.out.format("update: %s -> %s\n", prev, dir);
				}
			}
			/************************************/

			keySet.put(key, dir);
		}
		return watchers.get(i);
	}

	private boolean other_subscribers_subscribe_to(String path) {
		return file_subscriptions.containsKey(path);
	}

	/* unsubscribe */
	public void stopWatching(String path) throws IOException {
		Path dir = Paths.get(path);
		WatchService watcher = file_subscriptions.get(path);
		WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE,
				ENTRY_MODIFY);
		ConcurrentHashMap<WatchKey, Path> keySet = keys.get(watchers
				.indexOf(watcher));

		if (true) {
			Path prev = keySet.get(key);
			if (prev == null) {
				System.out.format(
						"This directory is not under subscription: %s\n", dir);
			} else {
				if (!dir.equals(prev)) {
					keySet.remove(key);
					System.out.format("removed subscription to: %s -> %s\n",
							prev, dir);
				}
			}
		}
		file_subscriptions.remove(path);
		key.cancel();
	}

	/* stop all threads in all pools */
	public void shutdown() {
		for (int i = 0; i < this.poolSize; i++) {
			pools.get(i).shutdown();
		}
	}

	/*
	 * all threads in all pools are kept alive throughout the lifetime of the
	 * server. So no thread spawned on the fly. Those threads that belong to
	 * pools that do not yet handle a subscription are simply alive and waiting.
	 */
	private void initializeThreadPools() throws IOException {
		this.watchers = new ArrayList<WatchService>();
		this.pools = new ArrayList<ExecutorService>();
		this.keys = new ArrayList<ConcurrentHashMap<WatchKey, Path>>();

		for (int i = 0; i < this.poolSize; i++) {
			pools.add(Executors.newFixedThreadPool(threadsPerPool));
			watchers.add(FileSystems.getDefault().newWatchService());
			keys.add(new ConcurrentHashMap<WatchKey, Path>());

			for (int j = 0; j < threadsPerPool; j++) {
				try {
					SubscriberThread subscriberThread = new SubscriberThread(
							watchers.get(watchers.size() - 1), keys.get(keys
									.size() - 1));
					pools.get(pools.size() - 1).execute(subscriberThread);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

		}
	}

}