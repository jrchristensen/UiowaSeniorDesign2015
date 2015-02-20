/*
The SD card is located on the ArduCam board and will be used using the SDFat library

The SD card and the Arducam are attached to the following pins of the arduino:
**MOSI - pin 11
**MISO - pin 12
**CLK  - pin 13
**CS   - pin 9  - as set in the variable cardSelect
**CS   - pin 10 - as set in the variable camSelect

*/
#include <DS3234.h>
#include <SPI.h>
#include <SdFat.h>
#include <SdFatUtil.h>
#include <Wire.h>
#include <ArduCAM.h>
#include "memorysaver.h"



/***************SD CARD VARIABLES******************/

const int cardSelect = 9;
Sd2Card card;
SdVolume volume;
SdFile root;
SdFile file;


/***************Serial Tansfer Variables **************/

boolean stringComplete = false;
String inputString;
char clientline[100];
char *filename;


/*******************ArduCAm Variables*******************/

#if defined(_arm_)
  #include <iota.h>
 #endif
 
const int camSelect = 10;
ArduCAM myCAM(OV2640,camSelect);
boolean isShowFlag = true;

/*************************RTC Variables******************/

// Init the DS3234
DS3234 rtc(52);

// Init a Time-data structure
Time t;

/*********************************************************/
void setup(){
  Serial.begin(115200);
  
  delay(1000);
 // init the SD card to half speed for trouble shooting
 // intit the SD card to full speed for better performance  
  if(!card.init(SPI_HALF_SPEED, cardSelect)){
    Serial.println("Error: The SD card didnt Initilize");
    Serial.println(card.errorCode());
    Serial.println(card.errorData());
    while(1);
  }
  
  //init a FAT volume
  if (!volume.init(&card)){
    Serial.println("Error: The Volume didn't Initilize");
    while(1);
  }
  // open the root folder
  if (!root.openRoot(&volume)){
    Serial.println("Error: The root failed to open");
    while(1);
  }
  /* Initilize the RTC */
  
  // Init the DS3234
  
    pinMode(51,OUTPUT);
    digitalWrite(51,HIGH);
    // Initialize the rtc object
    rtc.begin();
  
  /* Initilize the camera and test the interface then set for image capture*/
  
  uint8_t vid,pid;
  uint8_t temp; 
  #if defined (__AVR__)
    Wire.begin(); 
  #endif
  #if defined(__arm__)
    Wire1.begin(); 
  #endif
 // Serial.println("ArduCAM Start!"); 
  // set the SPI_CS as an output:
  pinMode(camSelect, OUTPUT);

  // initialize SPI:
  SPI.begin(); 
 
  //Check if the ArduCAM SPI bus is OK
  myCAM.write_reg(ARDUCHIP_TEST1, 0x55);
  temp = myCAM.read_reg(ARDUCHIP_TEST1);
  if(temp != 0x55)
  {
  	Serial.println("ERROR: SPI interface Error!");
  	while(1);
  }
  
  //Change MCU mode
  myCAM.write_reg(ARDUCHIP_MODE, 0x00);

   //Check if the camera module type is OV2640
  myCAM.rdSensorReg8_8(OV2640_CHIPID_HIGH, &vid);
  myCAM.rdSensorReg8_8(OV2640_CHIPID_LOW, &pid);
  if((vid != 0x26) || (pid != 0x42))
  	Serial.println("Can't find OV2640 module!");
  else
  	//Serial.println("OV2640 detected");
  	
  //Change to BMP capture mode and initialize the OV2640 module	  	
  myCAM.set_format(BMP);

  myCAM.InitCAM();
}



void serialEvent() {
  //Serial.println("Serial Event"); //used for first recognition of event on GUI
  while (Serial.available() && !stringComplete) {
    // get the new byte of data
    char inChar = Serial.read();
   
    // add it to the inputString:
    inputString += inChar;
    
    // if the incoming character is a newline, set a flag
    // so the main loop can do something about it:
        if (inChar == '\n') {
      stringComplete = true;
    }
    
  }
  if (inputString == "getfilenames\n" && stringComplete){  
   root.ls(); 
   inputString="";
   stringComplete=false;
  }
  if (inputString == "get\n" && stringComplete){ 
    inputString="";
    stringComplete=false;
    writeFiles();
  }
  if(inputString == "getcardinfo\n" && stringComplete){
    
    // print out the type of the volume
    PgmPrint("Volume is FAT");
    Serial.println(volume.fatType(),DEC);
    Serial.println();
    // list all the files found in the curent directory
    PgmPrintln("Files found in root:");
    root.ls(LS_DATE | LS_SIZE);
    Serial.println();
    // list all files found in all of the directories 
    PgmPrintln("Files found in all Directories");
    root.ls(LS_R);
    Serial.println();
    //The directory listing is done
    PgmPrintln("Done");
    
  }
  
}
void loop(){
  
 //captureImage();
  
}

void captureImage(){
  char str[8];
  char fileName[8];
  SdFile outFile;
  byte buf[256];
  static int i = 0;
  static int k = 0;
  static int n = 0;
  uint8_t temp,temp_last;
  uint8_t started_capture = 0;
  int total_time = 0;

    myCAM.write_reg(ARDUCHIP_MODE, 0x00);
    myCAM.set_format(JPEG);
    myCAM.InitCAM();

    myCAM.OV2640_set_JPEG_size(OV2640_1600x1200);
    delay(1000);
    
    //Flush the FIFO 
    myCAM.flush_fifo();	
    //Clear the capture done flag 
    myCAM.clear_fifo_flag();		 
    //Start capture
    myCAM.start_capture();
    //Serial.println("Start Capture");     
    while(!started_capture){
   
      if(myCAM.read_reg(ARDUCHIP_TRIG) & CAP_DONE_MASK){
        started_capture=1;
      }
        
      //  Serial.println("waiting");
    }
    //Serial.println("Capture Done!");
    /*
    //Construct a file name
    t=rtc.getTime();
    k = t.date;
    itoa(k, str, 10);
    Serial.println(str);
    strcpy(fileName,str);
    //strcat(fileName,"M");
     k = t.mon;
    itoa(k, str, 10);
    Serial.println(str);
    strcat(fileName,str);
    //strcat(fileName,"D");
    k = t.hour;
    itoa(k, str, 10);
    Serial.println(str);
    strcat(fileName,str);
    //strcat(fileName,"h");
    k = t.min;
    itoa(k, str, 10);
    Serial.println(str);
    strcat(fileName,str);
    //strcat(fileName,"m");
    k = t.sec;
    itoa(k, str, 10);
    Serial.println(str);
    strcat(fileName,str);
    //strcat(fileName,"s");
   
    strcat(fileName,".jpg");
    Serial.println(fileName);
    */
    //Construct a file name
    k = k + 1;
    itoa(k, str, 10); 
    strcat(str,".jpg");
    
    //Open the new file  
    
    if (! outFile.open(&root, str, O_RDWR | O_CREAT | O_AT_END)) 
    { 
      Serial.println("ERROR: File Failed to open");
      while(1);
      //return;
    }
    total_time = millis();
    i = 0;
    temp = myCAM.read_fifo();
    //Write first image data to buffer
    buf[i++] = temp;

    //Read JPEG data from FIFO
    while( (temp != 0xD9) | (temp_last != 0xFF) )
    {
      outFile.write(temp);
      temp_last = temp;
      temp = myCAM.read_fifo();
      
   }
    //Close the file 
    outFile.close(); 
    total_time = millis() - total_time;
    //Serial.print("Total time used:");
    Serial.print(total_time, DEC);
    Serial.println(" millisecond");    
    //Clear the capture done flag 
    myCAM.clear_fifo_flag();
    //Clear the start capture flag
    myCAM.set_format(BMP);
    myCAM.InitCAM();
 
 
}

void writeFiles(){
  uint16_t c;
  int index=0;
  //Wait for the serial to become availabel
  while(!Serial.available());
  
  //Start retrieving the fileNames from the Serial connection
  while (Serial.available() && !stringComplete){
    // read in the characters from the serial connection
    char inChar =Serial.read();
    // use the endline character as the termination of each of the filenames
    if (inChar != '\n'){
     clientline[index]= inChar;
     index++;
    }
    // use the # character to determine that all of the desired files have been completed 
    else if (inChar =='#'){
      return;
    }
    // set the set the filename to the contents of the buffer and open the 
    // file to begin the reading and the writing of the data to the serial port
   else{ 
     index=0;
     filename=clientline;
     if(file.open(&root, filename, O_READ)){
       while(file.curPosition()<file.fileSize()){
         Serial.write(file.read());
       }
      file.close();
      file.remove(&root, filename);
     }
     else{
       Serial.println("ERROR: File Cannot Be Opened"+inputString);
     }
       for (int i =0; i<sizeof(clientline); i++){
          clientline[i]=(char)0;
       }
   }
   // Wait for serial to become present again
   while(!Serial.available());
  }
}
