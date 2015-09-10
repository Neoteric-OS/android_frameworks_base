#ifndef AIDL_MACROS_H_
#define AIDL_MACROS_H_

#if LANG_CXX11
#define DISALLOW_COPY_AND_ASSIGN(TypeName) \
  TypeName(const TypeName&) = delete;      \
  void operator=(const TypeName&) = delete
#else
#define DISALLOW_COPY_AND_ASSIGN(TypeName) \
  TypeName(const TypeName&);               \
  void operator=(const TypeName&)
#endif

#endif // AIDL_MACROS_H_
