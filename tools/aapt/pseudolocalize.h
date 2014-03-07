#ifndef HOST_PSEUDOLOCALIZE_H
#define HOST_PSEUDOLOCALIZE_H

#include <string>

std::string pseudolocalize_string(const std::string& source);
// Surrounds every word in the sentance with specific characters
// that makes the word directionality RTL.
std::string pseudobidi_string(const std::string& source);
// Generates expansion string based on the specified lenght.
// Generated string could not be shorter that length, but it could be slightly
// longer.
std::string pseudo_generate_expansion(const unsigned int length);

#endif // HOST_PSEUDOLOCALIZE_H

