package com.simtechdata.googleauthdecoder;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ResourceList;
import io.github.classgraph.ScanResult;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Decoder {

	private enum OS {
		WINDOWS,
		MAC,
		LINUX
	}

	/**
	 * Main constructor that will be used to prime the library with your QR image file.
	 * @param imageFile - a java.nio.File object
	 */
	public Decoder(File imageFile) {
		this.size = 1000;
		this.imageFile = imageFile;
		if(getOS().equals(OS.WINDOWS))
			this.workFolder = new File(System.getenv("APPDATA"),"GoogleAuthDecoder");
		else
			this.workFolder = new File(userHome,".googleAuthDecoder");
		Path outFilePath = Paths.get(workFolder.getAbsolutePath(),source,outFilename);
		outFile = outFilePath.toFile();
		workFolder.mkdir();
		makeOutFolders();
		copyFiles();
	}

	/**
	 * Alternate constructor that will be used to prime the library with your QR image file
	 * as well as the size of your png image file. If your png file is larger than 1000 x 1000
	 * pixels, then round to the next highest 50 value and pass it into this constructor. So
	 * an image of 1310 x 1310 would get passed in using a size of 1350.
	 * @param imageFile - a java.nio.File object
	 * @param size - an integer of the size of your png file rounded to the next 50
	 */
	public Decoder(File imageFile, int size) {
		this.size = size;
		this.imageFile = imageFile;
		if(getOS().equals(OS.WINDOWS))
			this.workFolder = new File(System.getenv("APPDATA"),"GoogleAuthDecoder");
		else
			this.workFolder = new File(userHome,".googleAuthDecoder");
		Path outFilePath = Paths.get(workFolder.getAbsolutePath(),source,outFilename);
		outFile = outFilePath.toFile();
		workFolder.mkdir();
		makeOutFolders();
		copyFiles();
	}

	private final File            imageFile;
	private final String          userHome      = System.getProperty("user.home");
	private final File            workFolder;
	private final File            outFile;
	private final int             size;
	private final String          OSystem       = System.getProperty("os.name").toLowerCase();
	private final String          source        = "source";
	private final String          outFilename   = "output.txt";
	private final String          sourcePycache = "pycache";
	private final String          pycache    = "__pycache__";
	private final List<com.simtechdata.googleauthdecoder.OTPRecord> otpRecords = new LinkedList<>();
	private       List<String>    otpLines;
	private       File            sourceFolder;
	private       File            pycacheFolder;

	/**
	 * This is the method that will do all the heavy lifting by
	 * converting the QR image into a single string, then passing that
	 * string into the Python script, then extracting all the account
	 * details from each account that is in the QR image.
	 */
	public void decode() {
		try {
			if(imageFile.exists()) {
				changeSize();
				String oauthString = extractStringFromQRFile();
				runProcess(oauthString);
				otpLines = FileUtils.readLines(outFile, StandardCharsets.UTF_8);
				if(otpLines.size() == 0) {
					System.out.println("No data was found, is the Python3.9 bin folder in your PATH environment variable?");
				}
				else {
					extractData();
				}
			}
			else {
				System.out.println("Image file does not exist.");
			}
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
		cleanup();
	}

	/**
	 * Use this method to get the list of OTPRecords that is generated
	 * from the decode() method.
	 */
	public List<com.simtechdata.googleauthdecoder.OTPRecord> getRecords() {
		return otpRecords;
	}

	/**
	 * Use this method to get the list of the raw strings that are
	 * generated from the Python script
	 */
	public List<String> getRawOTP() {
		return otpLines;
	}

	private void cleanup() {
		try {
			FileUtils.deleteDirectory(workFolder);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static void info() {
		String info = """
       			In order to use this library, you must have Python 3.9 installed and the bin folder of your Python 3.9 installation
       			must be in your path environment variable.
    			
    			In addition to Python 3.9, you must run this from your command line at least one time so that the resource is available
    			to the library when it does the conversion:
    			
    			pip3.9 install click
    			
    			The rest is easy.
    			
    			First, open Google Authenticator and tap on the ellipses at the upper right and chose to export your accounts.
    			
    			Next, click on continue then authenticate however it asks you to do so.
    			
    			All of the accounts should be selected at which point you click on Export at the bottom right of the screen.
    			
    			Take a screen shot and save it to wherever you're going to use this library.
    			
    			You can click on CANCEL at the upper right instead of Next, because when you click on Next, it's going to want to delete all
    			of your accounts in Google Authenticator and that might not be of interest to you.
    			
    			The last thing you need to do, is crop that screen shot so that it only has the QR code in the image, then save it as a .PNG file.
    			
    			Use that file as the File object that you pass into the constructor.
    			 
    			Simply instantiate the library by passing the java.nio.File object into the constructor, where the File
    			points to the CROPPED screen shot that you took from Google Authenticator.
    			
    			if your cropped image is larger than 1,000 pixels in either dimension, then use the second constructor and pass in a size
    			that is the next 50 number above your image size. So, for example, if your image is 1430 x 1430, then pass in 1450 as the size.
    			""";
		System.out.println(info);
	}

	private void extractData() {
		String otpAuthString = "";
		String otpType = "";
		String otpName = "";
		String otpParams = "";
		String algorithm = "";
		String digits = "";
		String issuer = "";
		String secret = "";
		for(String oauth : otpLines) {
			String[] items = oauth.split("&");
			String[] otpauthItems = items[0].split("\\?");
			List<String> itemsList = new ArrayList<>(Arrays.asList(items));
			List<String> otpauthItemsList = new ArrayList<>(Arrays.asList(otpauthItems));
			for(String item : otpauthItemsList) {
				if (item.contains("otpauth:")) {
					otpAuthString = decodeHTML(item);
					otpType = getType(otpAuthString);
					otpName = getName(otpAuthString);
					otpParams = getParams(oauth);
				}
				if(item.contains("algorithm=")) {
					algorithm = item.replace("algorithm=","");
				}
			}
			for(String item : itemsList) {
				if (item.contains("digits=")) {
					digits = item.replace("digits=","");
				}
				if(item.contains("issuer=")) {
					issuer = item.replace("issuer=","");
				}
				if(item.contains("secret=")) {
					secret = item.replace("secret=","");
				}
			}
			otpRecords.add(new OTPRecord(otpAuthString,otpType,otpName,otpParams,algorithm,digits,issuer,secret));
		}
	}

	private String decodeHTML(String source) {
		String regex = "(%[a-zA-Z0-9]{2})";
		Pattern p = Pattern.compile(regex);
		Matcher m = p.matcher(source);
		String outString = source;
		while(m.find()) {
			outString = outString.replace(m.group(1), HTMLDecoding.get(m.group(1)));
		}
		return outString;
	}

	private String getType(String source) {
		String regex = "(otpauth://)([a-zA-Z0-9]+)(/)";
		Pattern p = Pattern.compile(regex);
		Matcher m = p.matcher(source);
		String outString = source;
		if(m.find()) {
			outString = m.group(2);
		}
		return outString;
	}

	private String getName(String source) {
		String regex = "(otpauth://)([a-zA-Z0-9]+)(/)(.+)(?)";
		Pattern p = Pattern.compile(regex);
		Matcher m = p.matcher(source);
		String outString = source;
		if(m.find()) {
			outString = m.group(4);
		}
		return outString;
	}

	private String getParams(String source) {
		String regex = "(otpauth://)(.+)(/)(.+)(\\?)(.+)(&)(.+)(&)(.+)(&)";
		Pattern p = Pattern.compile(regex);
		Matcher m = p.matcher(source);
		String outString = "";
		if(m.find()) {
			outString = m.group(6);
		}
		return outString;
	}

	private String decodeBufferedQRImage(BufferedImage bufferedImage) {
		Result result = null;
		try {
			LuminanceSource source        = new BufferedImageLuminanceSource(bufferedImage);
			BinaryBitmap    bitmap        = new BinaryBitmap(new HybridBinarizer(source));
			result = new MultiFormatReader().decode(bitmap);
			byte[] resultBytes = result.getRawBytes();
			String lastOTPAuth = result.getText();
		} catch (NotFoundException e) {e.printStackTrace();}
		return (result == null) ? "" : result.getText();
	}

	private String extractStringFromQRFile() {
		boolean invalidQRCode = true;
		String otpAuth = "";
		try {
			BufferedImage bufferedImage = ImageIO.read(imageFile);
			Image         image         = bufferedImage.getScaledInstance(500, 500, 1);
			otpAuth = decodeBufferedQRImage(bufferedImage);
			invalidQRCode = otpAuth.equals("");
		} catch (IOException e) {e.printStackTrace();}
		if (!invalidQRCode) return otpAuth; else return null;
	}

	private void changeSize() {
		try {
			int w = size, h = size;
			BufferedImage inputImage = ImageIO.read(imageFile);
			BufferedImage img = new BufferedImage(w, h, inputImage.getType());
			Graphics2D g = img.createGraphics();
			g.drawImage(inputImage, 0, 0, w, h, null);
			g.dispose();
			String name = imageFile.getName().substring(imageFile.getName().lastIndexOf(".") + 1).toUpperCase();
			ImageIO.write(img, name, imageFile);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private LinkedList<URL> getResourceFolderFiles (String subFolder) {
		String folder = "pythonSource/" + subFolder;
		LinkedList<URL> fileList = new LinkedList<>();
		try (ScanResult scanResult = new ClassGraph().enableAllInfo().scan()) {
			ResourceList resources = scanResult.getAllResources();
			for(URL url : resources.getURLs()) {
				if(url.toString().contains(folder)) {
					fileList.addLast(url);
				}
			}
		}
		return fileList;
	}

	private void makeOutFolders() {
		sourceFolder  = new File(workFolder,source);
		pycacheFolder = new File(sourceFolder, pycache);
		if (!sourceFolder.exists()) sourceFolder.mkdir();
		if (!pycacheFolder.exists()) pycacheFolder.mkdir();
	}

	private void copyFiles() {
		try {
			LinkedList<URL> srcFileList = getResourceFolderFiles(source);
			LinkedList<URL> pycacheFiles = getResourceFolderFiles(sourcePycache);
			for(URL url : srcFileList) {
				String filename = FilenameUtils.getName(url.toString());
				File outFile = new File(sourceFolder, filename);
				FileUtils.copyURLToFile(url,outFile);
			}
			for(URL url : pycacheFiles) {
				String filename = FilenameUtils.getName(url.toString());
				File outFile = new File(pycacheFolder, filename.replace(".fil", ""));
				FileUtils.copyURLToFile(url,outFile);
			}
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private OS getOS() {
		if (OSystem.toLowerCase().contains("win")) {return OS.WINDOWS;}
		else if (OSystem.toLowerCase().contains("mac")) {return OS.MAC;}
		else {return OS.LINUX;}
	}

	private record StreamGobbler(InputStream inputStream, Consumer<String> consumer) implements Runnable {
		@Override
		public void run() {
			new BufferedReader(new InputStreamReader(inputStream)).lines()
																  .forEach(consumer);
		}
	}

	private void runProcess(String oauthString) {
		try {
			ProcessBuilder builder = new ProcessBuilder();
			String command = "python3.9 decoder.py --convert \"" + oauthString + "\" > output.txt";
			if(getOS().equals(OS.WINDOWS))
				builder.command("cmd.exe", "-c", command);
			else
				builder.command("sh", "-c", command);
			File workingFolder = new File(workFolder,source);
			builder.directory(workingFolder);
			Process process = builder.start();
			StreamGobbler streamGobbler =
					new StreamGobbler(process.getInputStream(), System.out::println);
			Executors.newSingleThreadExecutor().submit(streamGobbler);
			int exitCode = process.waitFor();
			assert exitCode == 0;
		}
		catch (IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
}
