import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jssc.SerialPort;
import jssc.SerialPortException;
import jssc.SerialPortList;

public class ArdSerial {
	/**
	 * private static final List<String> USUAL_PORTS - List of the usual serial
	 * ports that the Arduino will connect to.
	 */
	private static final List<String> USUAL_PORTS = Arrays.asList("COM1",
			"COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
			"COM10");

	/**
	 * private String ardPort - the string representation of the port name the
	 * arduino is connected to
	 */
	private String ardPort;

	/**
	 * private string saveLocation - the location that the downloaded files will
	 * be saved
	 */
	private String saveLocation = "";
	/**
	 * private SerialPort serPort - The serial port connection with the Arduino
	 */
	private SerialPort serPort;

	/**
	 * private ArrayList fileNames - The names of the files that are contained
	 * on the SD card
	 */
	private ArrayList<String> fileNames = new ArrayList<String>();
	/**
	 * private StringBuilder sb - StringBuilder that is used to build the
	 * strings when the file names are sent from the Arduino
	 */
	private StringBuilder sb = new StringBuilder();

	/**
	 * public constructor ArdSerial
	 */
	public ArdSerial() {
		ardPort = "";
	}

	/**
	 * public constructor ArdSerial -
	 */
	public ArdSerial(String port) {
		ardPort = port;
	}

	/**
	 * public method connect - looks for a valid serial port with an Arduino
	 * board connected. If it is found, it's opened and a listener is added, so
	 * everytime a line is returned the stringProperty is set with that line.
	 * For that a StringBuilder is used to store the chars and extract the line
	 * content whenever '\r' or '\n' is found.
	 * 
	 * @return boolean connected - true if there was an Arduino is found and
	 *         connection is established
	 */
	public boolean connect() {
		Arrays.asList(SerialPortList.getPortNames())
				.stream()
				.filter(name -> ((!ardPort.isEmpty() && name.equals(ardPort)) || (ardPort
						.isEmpty() && USUAL_PORTS.stream().anyMatch(
						p -> name.startsWith(p)))))
				.findFirst()
				.ifPresent(
						name -> {
							try {
								serPort = new SerialPort(name);
								System.out.println("Connecting to "
										+ serPort.getPortName());
								if (serPort.openPort()) {
									serPort.setParams(
											SerialPort.BAUDRATE_115200,
											SerialPort.DATABITS_8,
											SerialPort.STOPBITS_1,
											SerialPort.PARITY_NONE);
									serPort.setFlowControlMode(serPort.FLOWCONTROL_RTSCTS_IN
											| serPort.FLOWCONTROL_RTSCTS_OUT);
									serPort.setEventsMask(SerialPort.MASK_RXCHAR);
									// serPort.addEventListener(event -> {});
								}
							} catch (SerialPortException e) {
								System.out.println("ERROR: Port '" + name
										+ "': " + e.toString());
							}
						});
		if (serPort != null)
			System.out.println("Connection Sucessful");
		else
			System.out.println("Connection Failed!");
		return serPort != null;
	}

	/**
	 * public method disconnect - method will remove the listener then check to
	 * see if the serial port is still connected. If it is still connected then
	 * the port will be closed
	 */
	public void disconnect() {
		if (serPort != null) {
			try {
				// serPort.removeEventListener();
				if (serPort.isOpened()) {
					serPort.closePort();
				}
			} catch (SerialPortException e) {
				System.out.println("ERROR closing port Exception:"
						+ e.toString());
			}
			System.out.println("Disconnecting: comm port closed");
		}
	}

	/**
	 * public method getFileNames() - gets the names of the files that are
	 * stored on the sd card that is attached to the Arduino. Then stores them
	 * in a string array to be used to create files during the file transfer.
	 */
	public void getFileNames() {
		try {
			// the following are the exact same call
			// serPort.writeBytes("g\n".getBytes());

			// serPort.writeInt(5);
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			serPort.writeString("getfilenames\n");
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		} catch (SerialPortException e) {
			e.printStackTrace();
		}
		try {
			while (serPort.getInputBufferBytesCount() != 0) {
				sb.append(serPort.readString());
				// for troubleshooting
				// System.out.println(sb.toString());
				String file = sb.toString();
				for (String name : file.split("\n")) {
					name = name.replace("\n", "").replace("\r", "");
					if (!name.contains("CONFIG")){
							fileNames.add(name);
					}
					// System.out.println(name);
				}
				/*
				 * if (file.endsWith("\n")) {
				 * 
				 * fileNames.add(file); sb = new StringBuilder(); file = ""; }
				 */

			}
		} catch (SerialPortException e) {
			e.printStackTrace();
		}
	}

	/**
	 * public method printFileNames - This method prints the file names to the
	 * Console. The method is used priamarly for troubleshooting and is mainly
	 * used only in development.
	 */
	public void printFileNames() {
		if (fileNames.size() > 0) {
			for (int i = 0; i < fileNames.size(); i++) {
				System.out.println(fileNames.get(i));
			}
		} else {
			System.out.println("There are no files to be printed");
		}
	}

	/**
	 * public method downloadFiles- Takes the file names that are stored in the
	 * ArrayList of file names and asks the Arduino to send the files through
	 * the Serial port. Each of the files will be created and be written to in
	 * the order that the file exist in the Arduino.
	 */
	public void downloadFiles() {
		try {
			serPort.writeString("get\n");
			System.out.println("lets get some files");
			Thread.sleep(200);
			for (int i = 0; i < fileNames.size(); i++) {
				System.out.println("Downloading file: "
						+ fileNames.get(i).toLowerCase());
				serPort.writeString(fileNames.get(i).toLowerCase() + "\n");
				Thread.sleep(200);

				File file = new File(saveLocation.concat(fileNames.get(i)
						.toLowerCase()));
				FileOutputStream fos = new FileOutputStream(file);
				boolean fileComplete = false;

				while (serPort.getInputBufferBytesCount() != 0) {
					byte[] in = serPort.readBytes();
					String s = new String(in);
					// System.out.println(s);
					if (s.contains("ERROR")) {
						// find the string that contains ERROR remove it and
						// finish writing to the file
						System.out.println(s);
						fos.write(in);
						// break;
					} else {
						fos.write(in);
						Thread.sleep(200);
					}
				}
				fos.close();

			}
			serPort.writeString("#");

		} catch (SerialPortException | InterruptedException | IOException e) {
			e.printStackTrace();
		}
	}

	public ArrayList<String> getImageFileNames() {
		ArrayList<String> images = new ArrayList<String>();

		for (String name : fileNames) {
			name = name.toLowerCase();
			if (name.endsWith(".jpg") || name.endsWith(".jpeg")
					|| name.endsWith(".png") || name.endsWith(".gif")
					|| name.endsWith(".bmp")) {
				images.add(name);
			}
		}

		return images;
	}

	/**
	 * public method setSaveLocation - sets the save location for the images to
	 * be saved to the computer
	 * 
	 * @param location
	 */
	public void setSaveLocation(String location) {
		saveLocation = location;

	}

	/**
	 * public method getSaveLocation - gets the save location of the imgaes to
	 * be saved to the computer
	 * 
	 * @return The string path of the save location
	 */
	public String getSaveLocation() {
		return saveLocation;
	}

	/**
	 * public method sendImageSize - sends the image size index to set the
	 * camera settings - the default setting is 1600 X 1200
	 * 
	 * 0 - 160 X 120
	 * 1 - 176 X 144 
	 * 2 - 320 X 240
	 * 3 - 352 X 288
	 * 4 - 640 X 480
	 * 5 - 800 X 600
	 * 6 - 1024 X 786
	 * 7 - 1280 X 1024
	 * 8 - 1200 X 1600
	 * 
	 * @param index
	 */
	public void sendImageSize(int index) {

		try {
			Thread.sleep(2000);
			serPort.writeString("setting\n");
			serPort.writeString(Integer.toString(index));
			Thread.sleep(2000);
		} catch (SerialPortException | InterruptedException e) {
			e.printStackTrace();
		}

	}

	/**
	 * public method sendDelayTime - sends the delay time to the Arduino to set
	 * the delay time between the image captures
	 * 
	 * @param delayTime - the delay time between captures
	 */
	public void sendDelayTime(int delayTime) {

		try {
			Thread.sleep(2000);
			serPort.writeString("delay\n");
			serPort.writeString(Integer.toString(delayTime));
			Thread.sleep(2000);
		} catch (SerialPortException | InterruptedException e) {
			e.printStackTrace();
		}

	}
	/**
	 * public method active - will set the camera active and inactive
	 * 						- will be used when down loading images from the camera 
	 */
	public void active(){
		try{
			Thread.sleep(1000);
			serPort.writeString("active\n");
			Thread.sleep(2000);
		}catch(Exception e){
			e.printStackTrace();
		}
	}

}
