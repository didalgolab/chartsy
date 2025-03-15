import matplotlib.pyplot as plt

# Przykładowe dane
x = [1, 2, 3, 4]
y = [10, 20, 15, 25]

plt.figure(figsize=(6,4))
plt.plot(x, y, marker='o')
plt.title("Przykładowy wykres")
plt.xlabel("X")
plt.ylabel("Y")

# Zapis wykresu do pliku PNG
plt.savefig("chart.png")
# Opcjonalnie: plt.show() nie jest potrzebne, bo wykres wyświetlamy w Javie