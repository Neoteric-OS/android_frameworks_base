# idmap format
version 0x01

```
idmap             := header data*
header            := magic version target_crc overlay_crc target_path overlay_path
data              := data_header data_block*
data_header       := target_package_id types_count
data_block        := target_type overlay_type entry_count entry_offset entry*
overlay_path      := string
target_path       := string
entry             := <uint32_t>
entry_count       := <uint16_t>
entry_offset      := <uint16_t>
magic             := <uint32_t>
overlay_crc       := <uint32_t>
overlay_type      := <uint16_t>
string            := <uint8_t>[256]
target_crc        := <uint32_t>
target_package_id := <uint16_t>
target_type       := <uint16_t>
types_count       := <uint16_t>
version           := <uint32_t>

# use some sort of short-hand notation instead?
header := magic<32> version<32> target_crc<32> overlay_crc<32> target_path<str> overlay_path<str>
```

# data structures
```
Idmap -> IdmapHeader + vector: IdmapData
                                      +-> IdmapData::Header
                                      +-> vector: IdmapData::TypeEntry
```

# test resources in both target and overlay
name           target      overlay
interger/int1  0x7f010000  0x7f010000
string/str1    0x7f020003  0x7f020000
string/str3    0x7f020005  0x7f020001

target
0x7f010000 integer/int1
0x7f020000 string/a
0x7f020001 string/b
0x7f020002 string/c
0x7f020003 string/str1
0x7f020004 string/str2
0x7f020005 string/str3
0x7f020006 string/x
0x7f020007 string/y
0x7f020008 string/z

overlay
0x7f010000 integer/int1
0x7f010001 integer/not\_in\_target
0x7f020000 string/str1
0x7f020001 string/str3


# idmap version 2
store res\_type, not (res\_type + 1); will impact test code and Idmap::GetEntryMapForType


# command line usage
$ idmap2 dump path/to/idmap  ->  PrettyPrintVisitor
$ idmap2 dump --verbose path/to/idmap  ->  DebugPrintVisitor (print every byte + annotation)
$ idmap2 verify path/to/idmap  ->  return EXIT\_SUCCESS if idmap is up to date
$ idmap2 help  ->  print help and usage
$ idmap2 create path/to/target path/to/overlay --idmap=path/to/idmap  ->  create and write idmap file
$ idmap2 scan ...  ->  scan for specific overlay packages, write results to file

Is it possible to get rid of the explicit --verify? The way installd calls
idmap today, --verify is used to make sure the idmap file isn't unnecessarily
truncated. The same logic could be moved into idmap2, which could call
ftruncate if new contents is to be created. Would this be OK wrt changing the
owner of the file?


# idmap2d
- remove SELinux rules for installd related to idmap
- remove ResTable::createIdmap
- remove overlays.list -- the AIDL interface will return a List<String> instead
    - also make idmap2d cache the results, so in a 32 + 64 bit zygote world a
      subsequent scan is not needed (requires mutex in impl)
- IdmapManager to query idmap2d for path to overlay file
- add trace logs everywhere
- remove Installer argument from OMS
    - replace IdmapManager(Installer) with IdmapManager(idmap2d), OMS to obtain
      handle to idmap2d
- add support to idmap2d for IBinder.DUMP\_TRANSACTION (implement
  BBinder::dump, see Binder.cpp)
- remove file descriptor argument to create: not needed if idmap2d runs as
  system and creates the files itself: work only with std::ostream objects
  internally
- move Create, Scan, etc to libidmap2/TopLevel.h?
