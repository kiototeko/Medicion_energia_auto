#include <18F2458.h>
#device ADC=12
#fuses HS, NOWDT, NOPROTECT, NOPUT, NOBROWNOUT, NOLVP, NOCPD, CPUDIV1
#use delay(clock=20000000)
#use rs232(uart1, baud=9600)
#include <string.h>
                          
//Necesitamos definir a que pin del microcontrolador corresponde cada pin de la pantalla LCD        
#define LCD_RS_PIN      PIN_B0                                 
#define LCD_RW_PIN      PIN_B2                                    
#define LCD_ENABLE_PIN  PIN_B3
#define LCD_DATA4       PIN_B4                                    
#define LCD_DATA5       PIN_B5                                    
#define LCD_DATA6       PIN_B6                                    
#define LCD_DATA7       PIN_B7  

#include <lcd.c>

#define MAX_BUF 17
#define MAX_PAYLOAD 32
#define VREF_H 5
#define VREF_L 0

#define RES_INV 2441
#define RES_INV_2 1220 
#define SENSIBILIDAD 45 //Es igual 469/(2565*sensibilidad) * 10
#define NUM_MUESTRAS_DIV 0.1 //10 muestras
#define NUM_MUESTRAS_OFFSET 0.01 //100 muestras
#define BASE_TIEMPO 0.1 //Tiempo durante el cual se hacen las mediciones
#define K_VOLTAJE 1313 //Constante de voltaje
#define E_ABS 30 //Error absoluto

/* FRACCION_NEG es un macro que sirve para que ya sea un argumento positivo o negativo, resulte siempre en 
un número positivo */
#define FRACCION_NEG(x) if(fraccional_##x < 0) \
							fraccional_##x *= -1

unsigned int16 corriente_adc = 0, voltaje_adc = 0;
int listo = 0, inicio = 0;
unsigned int32 offset = 0;


# INT_AD  
void interrupcion_ad(void)
{
	static int num_adc = 0, cambio = 0;
   	unsigned int16 valor_adc = READ_ADC(ADC_READ_ONLY); //Se lee un valor del ADC
   
//Dependiendo de si es la primera conversión (la de calibración) se ejecuta otro código
	if(inicio){
	   	delay_us(9);
	   	if(!cambio){ //se alternan las mediciones de corriente y voltaje para obtener un total de 20
			voltaje_adc += valor_adc;
			set_adc_channel(1);
		}
		else{
			corriente_adc += valor_adc;
			set_adc_channel(0);
		}  
	   	cambio = cambio? 0 : 1;
	
	   	++num_adc;
	
		
	   	if(num_adc == 20){ //Cuando ya se tienen las 20 muestras se indica al proceso principal con la variable list
			listo = 1;
			num_adc = 0;
	   	}
	   	else{
	   		READ_ADC(ADC_START_ONLY);
		}
	}
	else{ //Código para la calibración
		offset += valor_adc;
		++num_adc;

		
		if(num_adc == 100){ //Cuando ya se tienen 100 muestras para el offset se continua con la toma de muestras de voltaje y corriente
			num_adc = 0;
			inicio = 1;
		}
		else
			READ_ADC(ADC_START_ONLY);
	}
}


void init_adc(void)
{
	setup_adc_ports(AN0_TO_AN1 | VSS_VDD); //Se inicializan los canales AN0 y AN1 para la conversión y toma como referencia a VSS y VDD
   	setup_adc(ADC_CLOCK_DIV_16 | ADC_TAD_MUL_20); //Cada TAD equivale a 16 veces el periodo de oscilación, y para la adquisición se utilizan 20 TAD
   	delay_us(10);
}

void print_lcd(char buf[MAX_BUF])
{
	//Se manda la cadena de caracteres buf al módulo LCD
	int i, fin = 0;
	for(i = 0; i < MAX_BUF-1; i++){
		if(! fin){
			if(buf[i])
				lcd_putc(buf[i]);
			else
				fin = 1;
		}
		else
			lcd_putc(0x20);
	}
}



int32 obtener_valor_voltaje (int32 val, int16 cte)
{
	int32 sb;
	
	/* La fórmula para obtener el voltaje difiere un poco aquí de la que se estableció en la tesis para hacerla un poco más precisa.
	RES_INV es igual a 1/(2^n) y RES_INV_2 1/(2*2^n) donde n es la resolución */
	sb = val * RES_INV + RES_INV_2;
	return (sb*(VREF_H - VREF_L)*0.0001 + E_ABS)*cte*0.01;
}

int32 obtener_valor_corriente (int32 val)
{
	char buf[MAX_BUF];
	signed int32 sb;

	if(val - offset > 0){ //Solo si no es una corriente negativa se procesa
		sb = (val - offset) * RES_INV;
		return ((sb*(VREF_H - VREF_L)*0.0001))*SENSIBILIDAD*0.1;
	}
	else
		return 0;		
}


void enviar_dato(signed int32 energia, signed int32 voltaje, signed int32 corriente)
{
	signed int32 fraccional_ene, fraccional_vol, fraccional_cor;
    signed int32 entera_ene, entera_vol, entera_cor;
	char buf[MAX_PAYLOAD] = {0};
	static int32 count = 0;
	static char kj[4] = " KJ";
	
	//Para imprimir los datos con el formato adecuado, se debe obtener la parte entera y fraccional por separado de cada variable
	entera_ene = energia * 0.001;
	fraccional_ene = energia - entera_ene*1000;
	entera_vol = voltaje * 0.001;
	fraccional_vol = voltaje - entera_vol*1000;
	entera_cor = corriente * 0.001;
	fraccional_cor = corriente - entera_cor*1000;
	FRACCION_NEG(ene);
	FRACCION_NEG(vol);
	FRACCION_NEG(cor);

	sprintf(buf, "%Ld.%03Lu",  entera_ene, ((int32)fraccional_ene));
	 
	//Este printf transmite los datos por medio del UART
	printf("%sP%Ld.%03LuV%Ld.%03LuI%LuT%LuC\n", buf, entera_vol, ((int32)fraccional_vol), 
			entera_cor, ((int32)fraccional_cor), (int32)(BASE_TIEMPO*1000.0), count);

	strcat(buf, KJ);

	lcd_send_byte(0,0x2);
	print_lcd(buf);

	memset(buf, 0,  MAX_PAYLOAD);

	lcd_send_byte(0,0xc0);
	sprintf(buf, "%Ld.%03Lu V %Ld.%03Lu A", entera_vol, ((int32)fraccional_vol), 
			entera_cor, ((int32)fraccional_cor));
	print_lcd(buf); //Este printf_lcd manda los resultados a la pantalla LCD

	count++;

}


void main()
{
   	unsigned int32 corriente, voltaje;
   	signed int32 potencia, energia = 0, energia_k = 0, entero;
   	char buf[MAX_BUF];


	//Inicializamos el módulo LCD y el ADC
   	lcd_init();
   	init_adc();
     

   	enable_interrupts(INT_AD);      //Activa la interrupción de ADC
   	enable_interrupts(GLOBAL);      //Activa las interrupciones
	
	
	
   	set_adc_channel(1); 

	delay_ms(10);
	READ_ADC(ADC_START_ONLY); //Empezamos el ADC

	while(! inicio); //Esperamos a que acabe la toma de muestras para el offset
	
	offset *= NUM_MUESTRAS_OFFSET; //Se saca el promedio de las muestras de offset
	offset = 73; //Este es el valor experimental que se sacó para el offset. Hace inútil todo el proceso de obtención del offset anterior

   	set_adc_channel(0);
   	READ_ADC(ADC_START_ONLY); //Vuelve a empezar el ADC 


   	while(true){
		if(listo){ //Cuando ya se han tomado las mediciones suficientes
			//output_high(PIN_C2);			

			listo = 0;
		
			//Se obtiene el valor real de voltaje y corriente multiplicados ambos por 10^3
			voltaje = obtener_valor_voltaje(((int32)(voltaje_adc*NUM_MUESTRAS_DIV)), K_VOLTAJE);
			corriente = obtener_valor_corriente(((int32)(corriente_adc*NUM_MUESTRAS_DIV)));
			
			voltaje_adc = 0;
			corriente_adc = 0;

			potencia = ((signed int32)voltaje)*corriente*0.001; //Se obtiene la potencia instantánea con un factor de 10^3
			energia += potencia*BASE_TIEMPO; //Se obtiene la energía
			if(energia >= 1000){ //1000 en la variable energía equivale a 1 J por el factor de 10^3
				 entero = energia*0.001;
				 energia_k += entero; //energia_k también está multiplicado por 10^3
				 energia -= entero*1000; //A energia se le resta el entero y se queda solamente con el residuo
			}
				 
			enviar_dato(energia_k, voltaje, corriente); //Se envían los datos a la pantalla LCD y al módulo de transmisión por radiofrecuencia
			
			delay_ms(35); //para que sean 100 ms
			READ_ADC(ADC_START_ONLY); //Empieza otras lecturas del ADC
			//output_low(PIN_C2);

		}
   	}
}
