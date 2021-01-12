import matplotlib.pyplot as plt
import serial
import serial.tools.list_ports
import time
from matplotlib.widgets import Button
from matplotlib.animation import FuncAnimation
from datetime import datetime
import tkinter as tk
from tkinter import filedialog
from tkinter import messagebox
from pathlib import Path
import sys
import re


pattern = re.compile("^[0-9]+\.[0-9]{3}P[0-9]+\.[0-9]{3}V[0-9]+\.[0-9]{3}I[0-9]+T[0-9]+C") # expresión regular para checar formato de trama

fig, ax = plt.subplots() # iniciar ventana con gráfica
tiempo, energia, voltaje, corriente, periodo = [], [], [], [], []
ln, = plt.plot([],[],'ro', animated=True) # gráfica será actualizada continuamente
axcorr = plt.axes([0.7, 0.9, 0.1, 0.075]) # intervalo de ejes de acuerdo a medida
axvolt = plt.axes([0.81, 0.9, 0.1, 0.075])
axenerg = plt.axes([0.59, 0.9, 0.1, 0.075])
bcorr = Button(axcorr, 'Corriente') # botones para cambiar entre medidas
bvolt = Button(axvolt, 'Voltaje')
benerg = Button(axenerg, 'Energía')

fig.canvas.set_window_title("Consumo energético") #título de ventana

dt = datetime.today()
fecha = dt.strftime("%d%m%y%H%M%S") # se obtiene fecha de hoy

# el siguiente código genera la ventana que pide el puerto serial al que está conectado el Arduino

root = tk.Tk()
root.title("Puerto serial")
root.protocol("WM_DELETE_WINDOW", lambda: (root.destroy(),sys.exit()))
root.pack_propagate(0)
root.geometry("500x200")


ports = list(serial.tools.list_ports.comports()) # obtiene lista de puertos seriales

devices = []

for p in ports:
        devices.append(p.device)


if(not devices):
	root.withdraw()
	messagebox.showerror("Error", "No hay puertos seriales disponibles")
	root.destroy()
	sys.exit()

tkvar = tk.StringVar(root)
tkvar.set(devices[0]) # despliega la lista de puertos seriales en la ventana



popupMenu = tk.OptionMenu(root, tkvar, *devices)
tk.Label(root,text="Escoge el puerto serial").pack(pady=10)

popupMenu.pack(padx=5, pady=20)
botonOK = tk.Button(root,text="Listo", width=40, command= lambda: (root.quit(),root.withdraw()))

botonOK.pack(padx=5,pady=20, side=tk.BOTTOM)


root.mainloop()

serial_path = tkvar.get() 

file_path = filedialog.askdirectory(title="Selecciona directorio donde guardar los datos") # despliega ventana para seleccionar directorio donde guardar datos
root.destroy()

if(not file_path):
        sys.exit()

# el siguiente código crea el archivo csv donde guardará los datos generados

data_folder = Path(file_path)
data_file = "medicion_" + fecha + ".csv"

f = open(data_folder / data_file, "w+")
f.write("Energía,Voltaje,Intensidad de corriente,Periodo,Tiempo\n")

# esta clase sirve para hacer funcionar los botones que se encuentran en la ventana con la gráfica

class Index(object):
	opcion = 1

	def grafica_corr (self, event):
		Index.opcion = 3

	def grafica_volt (self, event):
		Index.opcion = 2

	def grafica_energ (self, event):
		Index.opcion = 1


callback = Index()
bcorr.on_clicked(callback.grafica_corr)
bvolt.on_clicked(callback.grafica_volt)
benerg.on_clicked(callback.grafica_energ)


switch = {
	'P': energia,
	'V': voltaje,
	'I': corriente,
	'T': periodo,
	'C': tiempo
}

# se abre el puerto serial

try:
	ser = serial.Serial(serial_path, timeout=5) #serial_path
except:
	messagebox.showerror("Error", "No se pudo abrir puerto serial")
	sys.exit()

def handle_close(event):
	f.close()

fig.canvas.mpl_connect('close_event', handle_close)

# obtener_valor es una función que extrae los valores de las tramas obtenidas y los almacena en las variables correspondientes

def obtener_valor(ser_str, data, c):
	ind = ser_str.find(c)
	data.append(float(ser_str[0:ind]))
	return ser_str[ind+1:]

# update se ejecuta de forma continua para actualizar la gráfica

def update(frames, energia,voltaje,corriente,periodo,tiempo):
	t = time.time()
	ser_str = ""

	switch_axis = {
		1: "Energía (KJ)",
		2: "Voltaje (V)",
		3: "Intensidad de corriente (A)"
	}

	# se obtiene una trama y se transforma al tipo de datos String
	try:
		ser_str = ser.readline().strip().decode('utf-8')
	except:
		return


	# se checa si cumple con el formato establecido
	if(not pattern.fullmatch(ser_str)):
		return

	# se extraen los valores de la trama
	ser_str = obtener_valor(ser_str, energia, "P")
	ser_str = obtener_valor(ser_str, voltaje, "V")
	ser_str = obtener_valor(ser_str, corriente, "I")
	ser_str = obtener_valor(ser_str, periodo, "T")

	# se obtiene el tiempo al cual corresponde la muestra
	ind = ser_str.find("C")
	tiempo.append(float(ser_str[0:ind])*periodo[-1]/1000)

	# se escriben los datos en el archivo abierto anteriormente
	f.write(str(energia[-1]) + "," + str(voltaje[-1]) + "," + str(corriente[-1]) + "," + str(periodo[-1]) + "," + str(tiempo[-1]) + "\n")

	# se grafican solo los últimos 100 datos
	energia = energia[-100:]
	tiempo = tiempo[-100:]
	voltaje = voltaje[-100:]
	corriente = corriente[-100:]
	periodo = periodo[-100:]

	switch_func = {
		1: energia,
		2: voltaje,
		3: corriente
	}


	ax.clear()
	xdata = switch_func.get(Index.opcion, 1)
	
	# se grafica la medición. xdata se refiere a una de las tres listas con las mediciones, su referencia cambia de acuerdo al botón que se aprieta.
	ax.plot(tiempo[::5], xdata[::5])
	ax.set_ylabel(switch_axis.get(Index.opcion, 1) + " [" + str(xdata[-1]) + "]")
	ax.set_xlabel("Tiempo (s)")
	ax.set_title("Consumo energético", loc='left')

	# si la medición es de energía, los ejes se van auto escalando, caso contrario se establecen de forma manual	
	if(Index.opcion > 1):
		ax.autoscale(axis='x')
		ax.set_ylim(min(xdata) - max(xdata), max(xdata)*2)
	else:	
		ax.autoscale(axis='both')

	ln.set_data(tiempo, xdata)

	return ln,


# esta función es la que hace que update se actualice de forma continua. interval determina cada cuanto se ejecuta (1 ms), fargs son las listas con las 
# mediciones
ani = FuncAnimation(fig, update, fargs=(energia,voltaje,corriente,periodo,tiempo), interval=1) 

plt.show()
