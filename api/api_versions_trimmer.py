#!/usr/bin/env python

import argparse
import zipfile
import xml.etree.ElementTree as ET

STUBS_FILE="out/soong/.intermediates/packages/modules/Wifi/framework/framework-wifi.stubs/android_common/turbine/framework-wifi.stubs.jar"
API_VERSIONS_FILE="prebuilts/sdk/current/public/data/api-versions.xml"

def fileToClass(filename):
    #drop `.class` suffix and replace folder delimiters with dots
    return filename[:-6]

def readClasses(stubs):
    """read classes from the stubs file
    stubs argument can be a path to a file (a string), a file-like object or a path-like object
    returns a set of the classes found in the file (set of strings)
    """
    classes = set()
    with zipfile.ZipFile(stubs) as zip:
        for info in zip.infolist():
            if not(info.is_dir()) and info.filename.endswith(".class") and not(info.filename.startswith("META-INF")):
                classes.add(fileToClass(info.filename))
    return classes

def filterLintDatabase(database, classesToRemove, output):
    """reads a lint database (jar file) and writes a slimmed down version that
    does include certain classes

    database argument: path to xml with lint database to read
    classesToRemove argument: iterable (ideally a set or similar for quick
    lookups) that enumerates the classes that should be removed
    output: path to write the filtered database
    """
    xml = ET.parse(database)
    root = xml.getroot()
    dbclasses = xml.findall("class")
    for c in xml.findall("class"):
        cname = c.get("name")
        if cname in classesToRemove:
            root.remove(c)
    xml.write(output)
    # classes = [cname for c in dbclasses if (cname := c.get("name")) not in classesToRemove]
    # print("Filtered", len(dbclasses) - len(classes), "classes")


def testStubs():
    classes = readClasses(STUBS_FILE)
    for c in classes:
        print(c)


def testDb():
    classes = readClasses(STUBS_FILE)
    filterLintDatabase(API_VERSIONS_FILE, classes, "res.xml")

def args():
  parser = argparse.ArgumentParser(description="Read a lint database (api-versions.xml) and many stubs jar files. Produce another database file that doesn't include the classes present in the stubs file(s).")
  parser.add_argument("output", help="Destination of the result (xml file).")
  parser.add_argument("api_versions", help="The lint database (api-versions.xml file) to read data from")
  parser.add_argument("stubs", nargs='+', help="The stubs jar file(s)")
  # should I support writing to stdout ?
  args = parser.parse_args()
  classes = set()
  for stub in args.stubs:
    classes.update(readClasses(stub))
  filterLintDatabase(args.api_versions, classes, args.output)


def main():
  #testStubs()
    #testDb()
    args()
    print("End")

if __name__ == "__main__":
  main()
