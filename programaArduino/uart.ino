String str, ind;
bool dato_P;

void setup() {
  // Un canal serial para el modulo de radiofrecuencia, otro para el modulo Bluetooth y otro para la computadora (depuracion)
  Serial.begin(9600);
  Serial1.begin(9600);
  Serial2.begin(9600);

}

void loop() {
  char c;


  if(Serial1.available() > 0){
    c = Serial1.read();
   
    if (c == '\n'){
      Serial.println(str);  
      Serial2.println(str);
      str = "";
    }
    else
      str +=c;
  }
}
