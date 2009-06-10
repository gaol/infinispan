#!/usr/bin/python

from __future__ import with_statement

import re
import subprocess
import os
import sys

VIEW_TO_USE = '3'
INPUT_FILE = "infinispan.log"
OUTPUT_FILE = "infinispan0.log"
addresses = {}
new_addresses = {}
def find(expr):
  with open(INPUT_FILE) as f:
    for l in f:
      if expr.match(l):
        handle(l, expr)
        break

def handle(l, expr):
  m = expr.match(l)
  print "Using JGROUPS VIEW line:"
  print "   %s" % l 
  members = m.group(1).strip()
  i = 1
  for m in members.split(','):
    addresses[m.strip()] = "CACHE%s" % i
    new_addresses["CACHE%s" % i] = m.strip()
    i += 1

def help():
  print '''
    INFINISPAN log file fixer.  Makes log files more readable by replacing JGroups addresses with friendly names.
  '''

def usage():
  print '''
    Usage: 
    $ bin/cleanlogs.py <N> <input_file> <output_file>

    N: (number) the JGroups VIEW ID to use as the definite list of caches.  Choose a view which has the most complete cache list.
    input_file: path to log file to transform
    output_file: path to result file

    ** All arguments are mandatory!
  '''

def main():
  help()

  ### Get args
  if len(sys.argv) != 4:
    usage()
    sys.exit(1)

  VIEW_TO_USE = int(sys.argv[1])
  INPUT_FILE = sys.argv[2]
  OUTPUT_FILE = sys.argv[3]

  expr = re.compile('.*Received new cluster view.*\|%s. \[(.*)\].*' % VIEW_TO_USE)
  find(expr)

  with open(INPUT_FILE) as f_in:
    with open(OUTPUT_FILE, 'w+') as f_out:
      for l in f_in:
        for c in addresses.keys():
          l = l.replace(c, addresses[c])
        f_out.write(l)

  print "Processed %s and generated %s.  The following replacements were made: " % (INPUT_FILE, OUTPUT_FILE)	
  sorted_keys = new_addresses.keys()
  sorted_keys.sort()
  for a in sorted_keys:
    print "  %s --> %s" % (new_addresses[a], a)

if __name__ == "__main__":
	main()
