
package com.esotericsoftware.wildcard;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Collects filesystem paths using wildcards, preserving the directory structure. Copies, deletes, and zips paths.
 */
public class Paths implements Iterable<String> {
	static private final Comparator<Path> LONGEST_TO_SHORTEST = new Comparator<Path>() {
		public int compare (Path s1, Path s2) {
			return s2.absolute().length() - s1.absolute().length();
		}
	};

	static private List<String> defaultGlobExcludes;

	final List<Path> paths = new ArrayList<Path>(32);

	/**
	 * Creates an empty Paths object.
	 */
	public Paths () {
		super();
	}

	/**
	 * Creates a Paths object and calls {@link #glob(String, String[])} with the specified arguments.
	 */
	public Paths (String dir, String... patterns) {
		super();
		glob(dir, patterns);
	}

	/**
	 * Collects all files and directories in the specified directory matching the wildcard patterns.
	 * @param dir The directory containing the paths to collect. If it does not exist, no paths are collected.
	 * @param patterns The wildcard patterns of the paths to collect or exclude. Patterns may optionally contain wildcards
	 *           represented by asterisks and question marks. If empty, null, or omitted then ** is assumed (collects all paths).<br>
	 * <br>
	 *           A single question mark (?) matches any single character. Eg, something? collects any path that is named
	 *           "something" plus any character.<br>
	 * <br>
	 *           A single asterisk (*) matches any characters up to the next slash (/). Eg, *\*\something* collects any path that
	 *           has two directories of any name, then a file or directory that starts with the name "something".<br>
	 * <br>
	 *           A double asterisk (**) matches any characters. Eg, **\something\** collects any path that contains a directory
	 *           named "something".<br>
	 * <br>
	 *           A pattern starting with an exclamation point (!) causes paths matched by the pattern to be excluded, even if other
	 *           patterns would select the paths.
	 */
	public void glob (String dir, String... patterns) {
		if (dir == null) dir = ".";
		File dirFile = new File(dir);
		if (!dirFile.exists()) return;

		List<String> includes = new ArrayList();
		List<String> excludes = new ArrayList();
		if (patterns != null) {
			for (String pattern : patterns) {
				if (pattern.charAt(0) == '!')
					excludes.add(pattern.substring(1));
				else
					includes.add(pattern);
			}
		}
		if (includes.isEmpty()) includes.add("**");

		if (defaultGlobExcludes != null) excludes.addAll(defaultGlobExcludes);

		GlobScanner scanner = new GlobScanner(dirFile, includes, excludes);
		String rootDir = scanner.rootDir().getPath().replace('\\', '/');
		if (!rootDir.endsWith("/")) rootDir += '/';
		for (String filePath : scanner.matches())
			paths.add(new Path(rootDir, filePath));
	}

	public void glob (String dirPattern) {
		String[] split = dirPattern.split("\\|");
		String[] patterns = new String[split.length - 1];
		for (int i = 1, n = split.length; i < n; i++)
			patterns[i - 1] = split[i];
		glob(split[0], patterns);
	}

	/**
	 * Collects all files and directories in the specified directory matching the regular expression patterns. This method is much
	 * slower than {@link #glob(String, String...)} because every file and directory under the specified directory must be
	 * inspected.
	 * @param dir The directory containing the paths to collect. If it does not exist, no paths are collected.
	 * @param patterns The regular expression patterns of the paths to collect or exclude. If empty, null, or omitted then no paths
	 *           are collected. <br>
	 * <br>
	 *           A pattern starting with an exclamation point (!) causes paths matched by the pattern to be excluded, even if other
	 *           patterns would select the paths.
	 */
	public void regex (String dir, String... patterns) {
		if (dir == null) dir = ".";
		File dirFile = new File(dir);
		if (!dirFile.exists()) return;

		List<String> includes = new ArrayList();
		List<String> excludes = new ArrayList();
		if (patterns != null) {
			for (String pattern : patterns) {
				if (pattern.charAt(0) == '!')
					excludes.add(pattern.substring(1));
				else
					includes.add(pattern);
			}
		}
		if (includes.isEmpty()) includes.add("**");

		RegexScanner scanner = new RegexScanner(dirFile, includes, excludes);
		String rootDir = scanner.rootDir().getPath().replace('\\', '/');
		if (!rootDir.endsWith("/")) rootDir += '/';
		for (String filePath : scanner.matches())
			paths.add(new Path(rootDir, filePath));
	}

	/**
	 * Copies the files and directories to the specified directory.
	 * @return A paths object containing the paths of the new files.
	 */
	public Paths copyTo (String destDir) throws IOException {
		Paths newPaths = new Paths();
		for (Path path : paths) {
			File destFile = new File(destDir, path.name);
			File srcFile = path.file();
			if (srcFile.isDirectory()) {
				destFile.mkdirs();
			} else {
				destFile.getParentFile().mkdirs();
				copyFile(srcFile, destFile);
			}
			newPaths.paths.add(new Path(destDir, path.name));
		}
		return newPaths;
	}

	/**
	 * Deletes all the files, directories, and any files in the directories.
	 * @return False if any file could not be deleted.
	 */
	public boolean delete () {
		boolean success = true;
		List<Path> pathsCopy = new ArrayList<Path>(paths);
		Collections.sort(pathsCopy, LONGEST_TO_SHORTEST);
		for (File file : getFiles(pathsCopy)) {
			if (file.isDirectory()) {
				if (!deleteDirectory(file)) success = false;
			} else {
				if (!file.delete()) success = false;
			}
		}
		return success;
	}

	/**
	 * Compresses the files and directories specified by the paths into a new zip file at the specified location. If there are no
	 * paths or all the paths are directories, no zip file will be created.
	 */
	public void zip (String destFile) throws IOException {
		Paths zipPaths = filesOnly();
		if (zipPaths.paths.isEmpty()) return;
		byte[] buf = new byte[1024];
		ZipOutputStream out = new ZipOutputStream(new FileOutputStream(destFile));
		try {
			for (Path path : zipPaths.paths) {
				File file = path.file();
				out.putNextEntry(new ZipEntry(path.name.replace('\\', '/')));
				FileInputStream in = new FileInputStream(file);
				int len;
				while ((len = in.read(buf)) > 0)
					out.write(buf, 0, len);
				in.close();
				out.closeEntry();
			}
		} finally {
			out.close();
		}
	}

	/**
	 * Returns the absolute paths delimited by the specified character.
	 */
	public String toString (String delimiter) {
		StringBuffer buffer = new StringBuffer(256);
		for (String path : getPaths()) {
			if (buffer.length() > 0) buffer.append(delimiter);
			buffer.append(path);
		}
		return buffer.toString();
	}

	/**
	 * Returns the absolute paths delimited by commas.
	 */
	public String toString () {
		return toString(", ");
	}

	/**
	 * Returns a Paths object containing the paths that are files.
	 */
	public Paths filesOnly () {
		Paths newPaths = new Paths();
		for (Path path : paths) {
			if (path.file().isFile()) newPaths.paths.add(path);
		}
		return newPaths;
	}

	/**
	 * Returns a Paths object containing the paths that are directories.
	 */
	public Paths dirsOnly () {
		Paths newPaths = new Paths();
		for (Path path : paths) {
			if (path.file().isDirectory()) newPaths.paths.add(path);
		}
		return newPaths;
	}

	/**
	 * Returns the paths as File objects.
	 */
	public File[] getFiles () {
		return getFiles(paths);
	}

	private File[] getFiles (List<Path> paths) {
		File[] files = new File[paths.size()];
		int i = 0;
		for (Path path : paths)
			files[i++] = path.file();
		return files;
	}

	/**
	 * Returns the relative paths.
	 */
	public String[] getRelativePaths () {
		String[] stringPaths = new String[paths.size()];
		int i = 0;
		for (Path path : paths)
			stringPaths[i++] = path.name;
		return stringPaths;
	}

	/**
	 * Returns the full paths.
	 */
	public String[] getPaths () {
		String[] stringPaths = new String[paths.size()];
		int i = 0;
		for (File file : getFiles())
			stringPaths[i++] = file.getPath();
		return stringPaths;
	}

	/**
	 * Returns the paths' filenames.
	 */
	public String[] getNames () {
		String[] stringPaths = new String[paths.size()];
		int i = 0;
		for (File file : getFiles())
			stringPaths[i++] = file.getName();
		return stringPaths;
	}

	/**
	 * Adds all paths from the specified Paths object to this Paths object.
	 */
	public void addAll (Paths paths) {
		this.paths.addAll(paths.paths);
	}

	/**
	 * Iterates over the absolute paths. The iterator supports the remove method.
	 */
	public Iterator<String> iterator () {
		return new Iterator<String>() {
			private Iterator<Path> iter = paths.iterator();

			public void remove () {
				iter.remove();
			}

			public String next () {
				return iter.next().absolute();
			}

			public boolean hasNext () {
				return iter.hasNext();
			}
		};
	}

	/**
	 * Iterates over the paths as File objects. The iterator supports the remove method.
	 */
	public Iterator<File> fileIterator () {
		return new Iterator<File>() {
			private Iterator<Path> iter = paths.iterator();

			public void remove () {
				iter.remove();
			}

			public File next () {
				return iter.next().file();
			}

			public boolean hasNext () {
				return iter.hasNext();
			}
		};
	}

	static private final class Path {
		public final String dir;
		public final String name;

		public Path (String dir, String name) {
			this.dir = dir;
			this.name = name;
		}

		public String absolute () {
			return dir + name;
		}

		public File file () {
			return new File(dir, name);
		}
	}

	/**
	 * Sets the exclude patterns that will be used in addition to the excludes specified for all glob searches.
	 */
	static public void setDefaultGlobExcludes (String... defaultGlobExcludes) {
		Paths.defaultGlobExcludes = Arrays.asList(defaultGlobExcludes);
	}

	/**
	 * Copies one file to another.
	 */
	static private void copyFile (File in, File out) throws IOException {
		FileChannel sourceChannel = new FileInputStream(in).getChannel();
		FileChannel destinationChannel = new FileOutputStream(out).getChannel();
		sourceChannel.transferTo(0, sourceChannel.size(), destinationChannel);
		sourceChannel.close();
		destinationChannel.close();
	}

	/**
	 * Deletes a directory and all files and directories it contains.
	 */
	static private boolean deleteDirectory (File file) {
		if (file.exists()) {
			File[] files = file.listFiles();
			for (int i = 0, n = files.length; i < n; i++) {
				if (files[i].isDirectory())
					deleteDirectory(files[i]);
				else
					files[i].delete();
			}
		}
		return file.delete();
	}

	public static void main (String[] args) {
		Paths paths = new Paths();
		// paths.regex("C:\\Java\\ls\\website", "[^tz]*");
		Paths.setDefaultGlobExcludes("**/.svn/**");
		paths.glob("C:\\Java\\ls", "website/**", "!misc");
		for (Path path : paths.paths)
			System.out.println(path.file());
	}
}
