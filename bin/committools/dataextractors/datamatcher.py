#!/usr/bin/env python
import csv
import sys

def readData(filename, keyRow):
	data = {}
	with open(filename,'r') as csvfile:
		reader = csv.reader(csvfile, delimiter=',')
		for row in reader:
			data[row[keyRow].strip()] = row[:keyRow] + row[keyRow+1:]

	return data 
	
def matchData(base, other):
	data = {}
	for baseKey in base:
		if baseKey in other:
			data[baseKey] = base[baseKey] + other[baseKey]
	return data
	
def writeData(data, filename):
	with open(filename,'w') as csvfile:
		writer = csv.writer(csvfile, delimiter=',')
		for baseKey in data:
			writer.writerow([baseKey]+data[baseKey])


if __name__== '__main__':
	base = readData(sys.argv[1], int(sys.argv[2]))
	other = readData(sys.argv[3], int(sys.argv[4]))
	data = matchData(base, other)
	writeData(data, sys.argv[5])
