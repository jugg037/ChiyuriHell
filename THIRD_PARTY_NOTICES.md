# Third-party notices

Chiyuri Desktop includes the following archive libraries and supporting components:

Apache Commons Compress includes transitive Apache-licensed Commons components; the Apache License 2.0 copy below applies to those dependencies as well.

- **Apache Commons Compress 1.28.0** — 7z archive reading. Licensed under Apache License 2.0; see [licenses/APACHE-2.0.txt](licenses/APACHE-2.0.txt). Upstream: https://commons.apache.org/proper/commons-compress/
- **XZ for Java 1.12** — optional 7z codec support. Licensed under 0BSD; see [licenses/XZ-0BSD-LICENSE.txt](licenses/XZ-0BSD-LICENSE.txt). Upstream: https://tukaani.org/xz/java.html
- **Junrar 8.1.2** — RAR archive reading/extraction only.
- **SLF4J API** (Junrar logging dependency) — MIT License; see [licenses/SLF4J-MIT-LICENSE.txt](licenses/SLF4J-MIT-LICENSE.txt). Distributed under the UnRAR license; see [licenses/UNRAR-LICENSE.txt](licenses/UNRAR-LICENSE.txt). Upstream: https://github.com/junrar/junrar

Junrar is used only to read and extract RAR archives. This project does not create or write RAR archives and does not implement a RAR-compatible archiver. The UnRAR license restriction must remain visible in the source and documentation.

Archive extraction is limited by Chiyuri Desktop to 1,000 entries and 256 MiB of expanded output per operation. 7z LZMA/LZMA2 dictionary memory is capped at 64 MiB; RAR dictionary memory is capped at 128 MiB. Unsupported codecs, encrypted archives with a missing/incorrect password, and multi-volume RAR sets may fail with an explanatory message.
