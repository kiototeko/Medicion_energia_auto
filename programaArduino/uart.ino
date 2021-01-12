String str, ind;
bool dato_P;

void setup() {
  // put your setup code here, to run once:
  Serial.begin(9600);
  Serial1.begin(9600);
  Serial2.begin(9600);

}

void loop() {
  // put your main code here, to run repeatedly:
  char c;


  if(Serial1.available() > 0){
    c = Serial1.read();
   
    /*
    if(c == 'P'){
      dato_P = true;
      str = "";
    }
    else if (c == 'C'){
      dato_P = false;
      ind = "";
    }
    */
    if (c == '\n'){
      Serial.println(str);  
      Serial2.println(str);
      str = "";
    }
    else
      str +=c;
    /*
    else{
      if(dato_P)
        str += c;
      else
        ind += c;
    }*/
  }
  /*
  if(payload[0] == 'P' && count[0] == 'C'){
      String str(&payload[1]), ind(&count[1]);
     
      // Now, resume listening so we catch the next packets.     
      Serial.print(F("Recibido "));
      Serial.println(str + ';' + ind);  
      Serial1.println(str + ';' + ind);
    }
    else if (payload[0] == 'P')
      falta = true;
    */
}
