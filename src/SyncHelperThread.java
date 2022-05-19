import java.io.File;
import java.rmi.Naming;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SyncHelperThread {

	public static long PERIOD_IN_MINUITES = 1;

	private static final String CLIENT_FOLDER = System.getProperty("user.dir") + "/clientfiles/";

	// Map Holds the filename and last modified date
	static HashMap<String, Long> oldFileNameToLastUpdatedMap = new HashMap<>();
	static HashMap<String, Long> newFileNameToLastUpdatedMap = new HashMap<>();

	// Set Holds the File Names
	static Set<String> newFileNameSet = new HashSet<>();
	static Set<String> oldFileNameSet = new HashSet<>();

	// This method periodically run in a configured intervals

	private static void run() {
		try {
			File fileObject = new File(CLIENT_FOLDER);
			File[] listFiles = fileObject.listFiles(); // Fetches the List of Files in A Directory
			FileOperations stub = (FileOperations) Naming.lookup("rmi://localhost:8080/FileOperations");
			System.out.println("***********Starting Sync Process *************");
			if (listFiles != null) {
				if (oldFileNameSet.size() == 0) {
					System.out.println("***********Initially Syncing All files to Server****************");
					for (File file : listFiles) {
						oldFileNameSet.add(file.getName());
						oldFileNameToLastUpdatedMap.put(file.getName(), file.lastModified());
						FileUploadClient.fileUpload(stub, file.getName()); // ReUsing Part 2 Upload Stub.
					}
				} else {
					for (File file : listFiles) {
						newFileNameSet.add(file.getName());
						newFileNameToLastUpdatedMap.put(file.getName(), file.lastModified());
					}

					Set<String> oldFilesTemp = new HashSet<String>();
					oldFilesTemp.addAll(oldFileNameSet);

					Set<String> newFilesTemp = new HashSet<>();
					newFilesTemp.addAll(newFileNameSet);

					newFilesTemp.removeAll(oldFileNameSet); // This will Give us the names of the newly created files.
					// Synching Newly created Files with the Server
					for (String fileName : newFilesTemp) {
						FileUploadClient.fileUpload(stub, fileName); // ReUsing Part 2 Upload Stub.
					}

					oldFilesTemp.removeAll(newFileNameSet); // This will Give Us Names of the Deleted Files

					// Synching deleted files with the server
					for (String fileName : oldFilesTemp) {
						FileDeleteClient.deleteFile(stub, fileName);
					}

					// Making Old files to New and New to empty
					oldFileNameSet = new HashSet<>();
					oldFileNameSet.addAll(newFileNameSet);
					newFileNameSet = new HashSet<>();

					// Checking Content of the File is Updated?
					if (newFileNameToLastUpdatedMap.size() == oldFileNameToLastUpdatedMap.size()) {
						for (String fileName : newFileNameToLastUpdatedMap.keySet()) {
							Long oldLastModified = oldFileNameToLastUpdatedMap.get(fileName);
							Long newLastModified = newFileNameToLastUpdatedMap.get(fileName);
							if (!oldLastModified.equals(newLastModified)) { // There is a update in the file
								FileUploadClient.fileUpload(stub, fileName); // ReUsing Part 2 Upload Stub.
							}
						}
					}

					// Copy Current File Last modified to Old and Make Current as Null.

					oldFileNameToLastUpdatedMap = new HashMap<String, Long>();
					oldFileNameToLastUpdatedMap.putAll(newFileNameToLastUpdatedMap);
					newFileNameToLastUpdatedMap = new HashMap<String, Long>();
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public static void main(String[] args) {
		ScheduledExecutorService executorService;
		executorService = Executors.newSingleThreadScheduledExecutor();
		executorService.scheduleAtFixedRate(SyncHelperThread::run, 0, PERIOD_IN_MINUITES, TimeUnit.MINUTES);
	}

}
