
#include <getopt.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <map>
#include <set>
#include <vector>

#include "Collation.h"
#include "frameworks/proto_logging/stats/atoms.pb.h"
#include "java_writer.h"
#include "java_writer_q.h"
#include "native_writer.h"
#include "utils.h"

using namespace google::protobuf;
using namespace std;

namespace android {
namespace stats_log_api_gen {

using android::os::statsd::Atom;

static void print_usage() {
    fprintf(stderr, "usage: stats-log-api-gen OPTIONS\n");
    fprintf(stderr, "\n");
    fprintf(stderr, "OPTIONS\n");
    fprintf(stderr, "  --cpp FILENAME       the header file to output for write helpers\n");
    fprintf(stderr, "  --header FILENAME    the cpp file to output for write helpers\n");
    fprintf(stderr, "  --help               this message\n");
    fprintf(stderr, "  --java FILENAME      the java file to output\n");
    fprintf(stderr, "  --module NAME        optional, module name to generate outputs for\n");
    fprintf(stderr,
            "  --namespace COMMA,SEP,NAMESPACE   required for cpp/header with "
            "module\n");
    fprintf(stderr,
            "                                    comma separated namespace of "
            "the files\n");
    fprintf(stderr,
            "  --importHeader NAME  required for cpp/jni to say which header to "
            "import "
            "for write helpers\n");
    fprintf(stderr, "  --javaPackage PACKAGE             the package for the java file.\n");
    fprintf(stderr, "                                    required for java with module\n");
    fprintf(stderr, "  --javaClass CLASS    the class name of the java class.\n");
    fprintf(stderr, "  --minApiLevel API_LEVEL           lowest API level to support.\n");
    fprintf(stderr, "                                    Default is \"current\".\n");
    fprintf(stderr,
            "  --worksource         Include support for logging WorkSource "
            "objects.\n");
    fprintf(stderr,
            "  --compileApiLevel API_LEVEL           specify which API level generated code is "
            "compiled against. (Java only).\n");
    fprintf(stderr,
            "                                        Default is \"current\".\n");
}

/**
 * Do the argument parsing and execute the tasks.
 */
static int run(int argc, char const* const* argv) {
    string cppFilename;
    string headerFilename;
    string javaFilename;
    string javaPackage;
    string javaClass;

    string moduleName = DEFAULT_MODULE_NAME;
    string cppNamespace = DEFAULT_CPP_NAMESPACE;
    string cppHeaderImport = DEFAULT_CPP_HEADER_IMPORT;
    bool supportWorkSource = false;
    int minApiLevel = API_LEVEL_CURRENT;
    int compileApiLevel = API_LEVEL_CURRENT;

    int index = 1;
    while (index < argc) {
        if (0 == strcmp("--help", argv[index])) {
            print_usage();
            return 0;
        } else if (0 == strcmp("--cpp", argv[index])) {
            index++;
            if (index >= argc) {
                print_usage();
                return 1;
            }
            cppFilename = argv[index];
        } else if (0 == strcmp("--header", argv[index])) {
            index++;
            if (index >= argc) {
                print_usage();
                return 1;
            }
            headerFilename = argv[index];
        } else if (0 == strcmp("--java", argv[index])) {
            index++;
            if (index >= argc) {
                print_usage();
                return 1;
            }
            javaFilename = argv[index];
        } else if (0 == strcmp("--module", argv[index])) {
            index++;
            if (index >= argc) {
                print_usage();
                return 1;
            }
            moduleName = argv[index];
        } else if (0 == strcmp("--namespace", argv[index])) {
            index++;
            if (index >= argc) {
                print_usage();
                return 1;
            }
            cppNamespace = argv[index];
        } else if (0 == strcmp("--importHeader", argv[index])) {
            index++;
            if (index >= argc) {
                print_usage();
                return 1;
            }
            cppHeaderImport = argv[index];
        } else if (0 == strcmp("--javaPackage", argv[index])) {
            index++;
            if (index >= argc) {
                print_usage();
                return 1;
            }
            javaPackage = argv[index];
        } else if (0 == strcmp("--javaClass", argv[index])) {
            index++;
            if (index >= argc) {
                print_usage();
                return 1;
            }
            javaClass = argv[index];
        } else if (0 == strcmp("--worksource", argv[index])) {
            supportWorkSource = true;
        } else if (0 == strcmp("--minApiLevel", argv[index])) {
            index++;
            if (index >= argc) {
                print_usage();
                return 1;
            }
            if (0 != strcmp("current", argv[index])) {
                minApiLevel = atoi(argv[index]);
            }
        } else if (0 == strcmp("--compileApiLevel", argv[index])) {
            index++;
            if (index >= argc) {
                print_usage();
                return 1;
            }
            if (0 != strcmp("current", argv[index])) {
                compileApiLevel = atoi(argv[index]);
            }
        }

        index++;
    }

    if (cppFilename.size() == 0 && headerFilename.size() == 0 && javaFilename.size() == 0) {
        print_usage();
        return 1;
    }
    if (DEFAULT_MODULE_NAME == moduleName &&
            (minApiLevel != API_LEVEL_CURRENT || compileApiLevel != API_LEVEL_CURRENT)) {
        // Default module only supports current API level.
        fprintf(stderr, "%s cannot support older API levels\n", moduleName.c_str());
        return 1;
    }

    if (compileApiLevel < API_R) {
        // Cannot compile against pre-R.
        fprintf(stderr, "compileApiLevel must be %d or higher.\n", API_R);
    }

    if (minApiLevel < API_Q) {
        // Cannot support pre-Q.
        fprintf(stderr, "minApiLevel must be %d or higher.\n", API_Q);
    }

    if (minApiLevel == API_LEVEL_CURRENT) {
        if (minApiLevel > compileApiLevel) {
            // If minApiLevel is not specified, assume it is the same as compileApiLevel.
            minApiLevel = compileApiLevel;
        }
    } else {
        if (minApiLevel > compileApiLevel) {
            // If specified, minApiLevel should always be lower than compileApiLevel.
            fprintf(stderr, "Invalid minApiLevel or compileApiLevel. If minApiLevel and"
                    " compileApiLevel are specified, minApiLevel should not be higher"
                    " than compileApiLevel.\n");
            return 1;
        }
    }

    // Collate the parameters
    Atoms atoms;
    int errorCount = collate_atoms(Atom::descriptor(), moduleName, &atoms);
    if (errorCount != 0) {
        return 1;
    }

    AtomDecl attributionDecl;
    vector<java_type_t> attributionSignature;
    collate_atom(android::os::statsd::AttributionNode::descriptor(), &attributionDecl,
                 &attributionSignature);

    // Write the .cpp file
    if (cppFilename.size() != 0) {
        FILE* out = fopen(cppFilename.c_str(), "w");
        if (out == NULL) {
            fprintf(stderr, "Unable to open file for write: %s\n", cppFilename.c_str());
            return 1;
        }
        // If this is for a specific module, the namespace must also be provided.
        if (moduleName != DEFAULT_MODULE_NAME && cppNamespace == DEFAULT_CPP_NAMESPACE) {
            fprintf(stderr, "Must supply --namespace if supplying a specific module\n");
            return 1;
        }
        // If this is for a specific module, the header file to import must also be
        // provided.
        if (moduleName != DEFAULT_MODULE_NAME && cppHeaderImport == DEFAULT_CPP_HEADER_IMPORT) {
            fprintf(stderr, "Must supply --headerImport if supplying a specific module\n");
            return 1;
        }
        errorCount = android::stats_log_api_gen::write_stats_log_cpp(
                out, atoms, attributionDecl, cppNamespace, cppHeaderImport, minApiLevel);
        fclose(out);
    }

    // Write the .h file
    if (headerFilename.size() != 0) {
        FILE* out = fopen(headerFilename.c_str(), "w");
        if (out == NULL) {
            fprintf(stderr, "Unable to open file for write: %s\n", headerFilename.c_str());
            return 1;
        }
        // If this is for a specific module, the namespace must also be provided.
        if (moduleName != DEFAULT_MODULE_NAME && cppNamespace == DEFAULT_CPP_NAMESPACE) {
            fprintf(stderr, "Must supply --namespace if supplying a specific module\n");
        }
        errorCount = android::stats_log_api_gen::write_stats_log_header(out, atoms, attributionDecl,
                                                                        cppNamespace);
        fclose(out);
    }

    // Write the .java file
    if (javaFilename.size() != 0) {
        if (javaClass.size() == 0) {
            fprintf(stderr, "Must supply --javaClass if supplying a Java filename");
            return 1;
        }

        if (javaPackage.size() == 0) {
            fprintf(stderr, "Must supply --javaPackage if supplying a Java filename");
            return 1;
        }

        if (moduleName.size() == 0) {
            fprintf(stderr, "Must supply --module if supplying a Java filename");
            return 1;
        }

        FILE* out = fopen(javaFilename.c_str(), "w");
        if (out == NULL) {
            fprintf(stderr, "Unable to open file for write: %s\n", javaFilename.c_str());
            return 1;
        }

        errorCount = android::stats_log_api_gen::write_stats_log_java(
                out, atoms, attributionDecl, javaClass, javaPackage, minApiLevel,
                supportWorkSource);

        fclose(out);
    }

    return errorCount;
}

}  // namespace stats_log_api_gen
}  // namespace android

/**
 * Main.
 */
int main(int argc, char const* const* argv) {
    GOOGLE_PROTOBUF_VERIFY_VERSION;

    return android::stats_log_api_gen::run(argc, argv);
}
