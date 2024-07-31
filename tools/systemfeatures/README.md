# Build-time system feature support

## Overview

Historically, system features have been defined and aggregated as
`<feature>` xml attributes  across various partitions, queried at runtime
through the framework. This directory contains tooling that will support
*build-time* queries of select system features, enabling optimizations
like code stripping when so configured.

### TODO(b/203143243): Expand readme after landing codegen.
