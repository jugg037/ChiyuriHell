# Third-party notices

Chiyuri Desktop includes the following archive libraries and supporting components:

Apache Commons Compress includes transitive Apache-licensed Commons components; the Apache License 2.0 copy below applies to those dependencies as well.

- **Apache Commons Compress 1.28.0** — 7z archive reading. Licensed under Apache License 2.0; see [licenses/APACHE-2.0.txt](licenses/APACHE-2.0.txt). Upstream: https://commons.apache.org/proper/commons-compress/
- **XZ for Java 1.12** — optional 7z codec support. Licensed under 0BSD; see [licenses/XZ-0BSD-LICENSE.txt](licenses/XZ-0BSD-LICENSE.txt). Upstream: https://tukaani.org/xz/java.html
- **Junrar 8.1.2** — RAR archive reading/extraction only.
- **SLF4J API** (Junrar logging dependency) — MIT License; see [licenses/SLF4J-MIT-LICENSE.txt](licenses/SLF4J-MIT-LICENSE.txt). Distributed under the UnRAR license; see [licenses/UNRAR-LICENSE.txt](licenses/UNRAR-LICENSE.txt). Upstream: https://github.com/junrar/junrar

Junrar is used only to read and extract RAR archives. This project does not create or write RAR archives and does not implement a RAR-compatible archiver. The UnRAR license restriction must remain visible in the source and documentation.

Archive extraction is limited by Chiyuri Desktop to 1,000 entries and 256 MiB of expanded output per operation. ZIP, 7z and RAR input files are limited to 512 MiB; its 64 MiB memory cap is honored by LZMA/LZMA2 codecs (not every 7z codec honors this setting). RAR dictionary memory is capped at 128 MiB. Extracted entries are flattened into the selected folder to prevent archive path traversal. ZIP AES/standard encryption and encrypted RAR/7Z archives require the password field. ZIP strong encryption is not supported. RAR5 multipart sets named .part1.rar, .part2.rar and so on are supported when every part is present. Legacy .r00-style multipart sets and unsupported codecs are not supported and may fail with an explanatory message.
