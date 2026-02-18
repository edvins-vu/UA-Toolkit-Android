#import <UIKit/UIKit.h>
#import <Foundation/Foundation.h>

// Helper to convert NSString to a C string that Unity can manage
static char* MakeStringCopy(const char* string) {
    if (string == NULL) return NULL;
    char* copy = (char*)malloc(strlen(string) + 1);
    strcpy(copy, string);
    return copy;
}

/**
 * Checks which URL schemes from the provided CSV list correspond to installed apps.
 * Called from Unity C# via [DllImport("__Internal")].
 *
 * Each game must register its own URL scheme in CFBundleURLTypes,
 * and the querying app must declare schemes in LSApplicationQueriesSchemes.
 *
 * @param schemesCsv Comma-separated URL schemes (e.g. "yourstudio-game1,yourstudio-game2")
 * @return Comma-separated list of schemes that are installed (caller must free)
 */
extern "C" {
    const char* CheckInstalledSchemes(const char* schemesCsv) {
        if (schemesCsv == NULL || strlen(schemesCsv) == 0) {
            return MakeStringCopy("");
        }

        NSString* input = [NSString stringWithUTF8String:schemesCsv];
        NSArray* schemes = [input componentsSeparatedByString:@","];
        NSMutableArray* installed = [NSMutableArray array];

        for (NSString* scheme in schemes) {
            NSString* trimmed = [scheme stringByTrimmingCharactersInSet:
                [NSCharacterSet whitespaceCharacterSet]];

            if (trimmed.length == 0) continue;

            NSString* urlString = [NSString stringWithFormat:@"%@://", trimmed];
            NSURL* url = [NSURL URLWithString:urlString];

            if (url && [[UIApplication sharedApplication] canOpenURL:url]) {
                [installed addObject:trimmed];
                NSLog(@"[InstalledAppsChecker] Installed: %@", trimmed);
            } else {
                NSLog(@"[InstalledAppsChecker] Not installed: %@", trimmed);
            }
        }

        NSString* result = [installed componentsJoinedByString:@","];
        return MakeStringCopy([result UTF8String]);
    }
}
