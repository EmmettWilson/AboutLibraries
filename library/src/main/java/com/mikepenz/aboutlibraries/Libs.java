package com.mikepenz.aboutlibraries;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.text.TextUtils;
import android.util.Log;

import com.mikepenz.aboutlibraries.detector.Detect;
import com.mikepenz.aboutlibraries.entity.Library;
import com.mikepenz.aboutlibraries.entity.License;
import com.mikepenz.aboutlibraries.util.GenericsUtil;
import com.mikepenz.aboutlibraries.util.Util;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Libs {
    public enum LibraryFields {
        AUTHOR_NAME,
        AUTHOR_WEBSITE,
        LIBRARY_NAME,
        LIBRARY_DESCRIPTION,
        LIBRARY_VERSION,
        LIBRARY_WEBSITE,
        LIBRARY_OPEN_SOURCE,
        LIBRARY_REPOSITORY_LINK,
        LIBRARY_CLASSPATH,
        LICENSE_NAME,
        LICENSE_SHORT_DESCRIPTION,
        LICENSE_DESCRIPTION,
        LICENSE_WEBSITE
    }

    public enum ActivityStyle {
        LIGHT,
        DARK,
        LIGHT_DARK_TOOLBAR
    }

    public enum SpecialButton {
        SPECIAL1,
        SPECIAL2,
        SPECIAL3
    }

    public static final String BUNDLE_THEME = "ABOUT_LIBRARIES_THEME";
    public static final String BUNDLE_TITLE = "ABOUT_LIBRARIES_TITLE";
    public static final String BUNDLE_STYLE = "ABOUT_LIBRARIES_STYLE";
    public static final String BUNDLE_COLORS = "ABOUT_COLOR";

    private static final String DEFINE_LICENSE = "define_license_";
    private static final String DEFINE_INT = "define_int_";
    private static final String DEFINE_EXT = "define_";

    private static final String DELIMITER = ";";

    private ArrayList<Library> internLibraries = new ArrayList<>();
    private ArrayList<Library> externLibraries = new ArrayList<>();
    private ArrayList<License> licenses = new ArrayList<>();

    public Libs(Context context) {
        String[] fields = GenericsUtil.getFields(context);
        init(context, fields);
    }

    public Libs(Context context, String[] fields) {
        init(context, fields);
    }

    /**
     * init method
     *
     * @param fields
     */
    private void init(Context ctx, String[] fields) {
        ArrayList<String> foundLicenseIdentifiers = new ArrayList<>();
        ArrayList<String> foundInternalLibraryIdentifiers = new ArrayList<>();
        ArrayList<String> foundExternalLibraryIdentifiers = new ArrayList<>();

        if (fields != null) {
            for (String field : fields) {
                if (field.startsWith(DEFINE_LICENSE)) {
                    foundLicenseIdentifiers.add(field.replace(DEFINE_LICENSE, ""));
                } else if (field.startsWith(DEFINE_INT)) {
                    foundInternalLibraryIdentifiers.add(field.replace(DEFINE_INT, ""));
                } else if (field.startsWith(DEFINE_EXT)) {
                    foundExternalLibraryIdentifiers.add(field.replace(DEFINE_EXT, ""));
                }
            }
        }

        // add licenses
        // this has to happen first as the licenses need to be initialized before the libraries are read in
        for (String licenseIdentifier : foundLicenseIdentifiers) {
            License license = genLicense(ctx, licenseIdentifier);
            if (license != null) {
                licenses.add(license);
            }
        }
        //add internal libs
        for (String internalIdentifier : foundInternalLibraryIdentifiers) {
            Library library = genLibrary(ctx, internalIdentifier);
            if (library != null) {
                library.setInternal(true);
                internLibraries.add(library);
            }
        }

        //add external libs
        for (String externalIdentifier : foundExternalLibraryIdentifiers) {
            Library library = genLibrary(ctx, externalIdentifier);
            if (library != null) {
                library.setInternal(false);
                externLibraries.add(library);
            }
        }
    }

    /**
     * A helper method to get a String[] out of a fieldArray
     *
     * @param fields R.strings.class.getFields()
     * @return a String[] with the string ids we need
     */
    public static String[] toStringArray(Field[] fields) {
        ArrayList<String> fieldArray = new ArrayList<String>();
        for (Field field : fields) {
            if (field.getName().contains(DEFINE_EXT)) {
                fieldArray.add(field.getName());
            }
        }
        return fieldArray.toArray(new String[fieldArray.size()]);
    }

    /**
     * This will summarize all libraries and elimate duplicates
     *
     * @param internalLibraries    the String[] with the internalLibraries (if set manual)
     * @param excludeLibraries     the String[] with the libs to be excluded
     * @param autoDetect           defines if the libraries should be resolved by their classpath (if possible)
     * @param checkCachedDetection defines if we should check the cached autodetected libraries (per version) (default: enabled)
     * @param sort                 defines if the array should be sorted
     * @return the summarized list of included Libraries
     */
    public ArrayList<Library> prepareLibraries(Context ctx, String[] internalLibraries, String[] excludeLibraries, boolean autoDetect, boolean checkCachedDetection, boolean sort) {
        boolean isExcluding = excludeLibraries != null;
        HashMap<String, Library> libraries = isExcluding? new HashMap<String, Library>():null;
        ArrayList<Library> resultLibraries = new ArrayList<>();

        if (autoDetect) {
            List<Library> autoDetected = getAutoDetectedLibraries(ctx, checkCachedDetection);
            resultLibraries.addAll(autoDetected);

            if(isExcluding) {
                for (Library lib : autoDetected) {
                    libraries.put(lib.getDefinedName(), lib);
                }
            }
        }

        //Add all external libraries
        List<Library> extern = getExternLibraries();
        resultLibraries.addAll(extern);

        if(isExcluding) {
            for (Library lib : extern) {
                libraries.put(lib.getDefinedName(), lib);
            }
        }

        //Now add all libs which do not contains the info file, but are in the AboutLibraries lib
        if (internalLibraries != null) {
            for (String internalLibrary : internalLibraries) {
                Library lib = getLibrary(internalLibrary);

                if(isExcluding && lib != null) {
                    resultLibraries.add(lib);
                    libraries.put(lib.getDefinedName(), lib);
                }
            }
        }

        //remove libraries which should be excluded
        if (isExcluding) {
            for (String excludeLibrary : excludeLibraries) {
                Library lib = libraries.get(excludeLibrary);
                if (lib != null) {
                    resultLibraries.remove(lib);
                }
            }
        }

        if (sort) {
            Collections.sort(resultLibraries);
        }
        return resultLibraries;
    }

    /**
     * Get all autoDetected Libraries
     *
     * @param ctx                  the current context
     * @param checkCachedDetection defines if we should check the cached autodetected libraries (per version) (default: enabled)
     * @return an ArrayList Library with all found libs by their classpath
     */
    public List<Library> getAutoDetectedLibraries(Context ctx, boolean checkCachedDetection) {
        List<Library> libraries;
        PackageInfo pi = Util.getPackageInfo(ctx);
        SharedPreferences sharedPreferences = ctx.getSharedPreferences("aboutLibraries", Context.MODE_PRIVATE);
        int lastCacheVersion = sharedPreferences.getInt("versionCode", -1);
        boolean isCacheUpToDate = pi != null && lastCacheVersion == pi.versionCode;

        if (checkCachedDetection) {//Retrieve from cache if up to date
            if (pi != null && isCacheUpToDate) {
                String[] autoDetectedLibraries = sharedPreferences.getString("autoDetectedLibraries", "").split(DELIMITER);

                if (autoDetectedLibraries.length > 0) {
                    libraries = new ArrayList<>(autoDetectedLibraries.length);
                    for (String autoDetectedLibrary : autoDetectedLibraries) {
                        Library lib = getLibrary(autoDetectedLibrary);
                        if (lib != null) libraries.add(lib);
                    }
                    return libraries;
                }
            }
        }

        libraries = Detect.detect(ctx, getLibraries());

        if (pi != null && !isCacheUpToDate) {//Update cache
            StringBuilder autoDetectedLibrariesPref = new StringBuilder();

            for (Library lib : libraries) {
                autoDetectedLibrariesPref.append(DELIMITER).append(lib.getDefinedName());
            }

            sharedPreferences.edit()
                    .putInt("versionCode", pi.versionCode)
                    .putString("autoDetectedLibraries", autoDetectedLibrariesPref.toString())
                    .apply();
        }

        return libraries;
    }

    /**
     * Get all intern available Libraries
     *
     * @return an ArrayList Library with all available internLibraries
     */
    public ArrayList<Library> getInternLibraries() {
        return new ArrayList<>(internLibraries);
    }

    /**
     * Get all extern available Libraries
     *
     * @return an ArrayList Library  with all available externLibraries
     */
    public ArrayList<Library> getExternLibraries() {
        return new ArrayList<>(externLibraries);
    }

    /**
     * Get all available licenses
     *
     * @return an ArrayLIst License  with all available Licenses
     */
    public ArrayList<License> getLicenses() {
        return new ArrayList<License>(licenses);
    }

    /**
     * Get all available Libraries
     *
     * @return an ArrayList Library with all available Libraries
     */
    public ArrayList<Library> getLibraries() {
        ArrayList<Library> libs = new ArrayList<>();
        libs.addAll(getInternLibraries());
        libs.addAll(getExternLibraries());
        return libs;
    }

    /**
     * Get a library by its name (the name must be equal)
     *
     * @param libraryName the name of the lib (NOT case sensitiv) or the real name of the lib (this is the name used for github)
     * @return the found library or null
     */
    public Library getLibrary(String libraryName) {
        for (Library library : getLibraries()) {
            if (library.getLibraryName().toLowerCase().equals(libraryName.toLowerCase())) {
                return library;
            } else if (library.getDefinedName().toLowerCase().equals(libraryName.toLowerCase())) {
                return library;
            }
        }
        return null;
    }

    /**
     * Find a library by a searchTerm (Limit the results if there are more than one)
     *
     * @param searchTerm the term which is in the libs name (NOT case sensitiv) or the real name of the lib (this is the name used for github)
     * @param limit      -1 for all results or smaller 0 for a limitted result
     * @return an ArrayList Library with the found internLibraries
     */
    public ArrayList<Library> findLibrary(String searchTerm, int limit) {
        return find(getLibraries(), searchTerm, false, limit);
    }

    /**
     * @param searchTerm
     * @param idOnly
     * @param limit
     * @return
     */
    public ArrayList<Library> findInInternalLibrary(String searchTerm, boolean idOnly, int limit) {
        return find(getInternLibraries(), searchTerm, idOnly, limit);
    }

    /**
     * @param searchTerm
     * @param idOnly
     * @param limit
     * @return
     */
    public ArrayList<Library> findInExternalLibrary(String searchTerm, boolean idOnly, int limit) {
        return find(getExternLibraries(), searchTerm, idOnly, limit);
    }

    /**
     * @param libraries
     * @param searchTerm
     * @param idOnly
     * @param limit
     * @return
     */
    private ArrayList<Library> find(ArrayList<Library> libraries, String searchTerm, boolean idOnly, int limit) {
        ArrayList<Library> localLibs = new ArrayList<>();

        int count = 0;
        for (Library library : libraries) {
            if (idOnly) {
                if (library.getDefinedName().toLowerCase().contains(searchTerm.toLowerCase())) {
                    localLibs.add(library);
                    count = count + 1;

                    if (limit != -1 && limit < count) {
                        break;
                    }
                }
            } else {
                if (library.getLibraryName().toLowerCase().contains(searchTerm.toLowerCase()) || library.getDefinedName().toLowerCase().contains(searchTerm.toLowerCase())) {
                    localLibs.add(library);
                    count = count + 1;

                    if (limit != -1 && limit < count) {
                        break;
                    }
                }
            }
        }

        return localLibs;
    }


    /**
     * @param licenseName
     * @return
     */
    public License getLicense(String licenseName) {
        for (License license : getLicenses()) {
            if (license.getLicenseName().toLowerCase().equals(licenseName.toLowerCase())) {
                return license;
            } else if (license.getDefinedName().toLowerCase().equals(licenseName.toLowerCase())) {
                return license;
            }
        }
        return null;
    }

    /**
     * @param licenseName
     * @return
     */
    private License genLicense(Context ctx, String licenseName) {
        licenseName = licenseName.replace("-", "_");

        try {
            License lic = new License();
            lic.setDefinedName(licenseName);
            lic.setLicenseName(getStringResourceByName(ctx, "license_" + licenseName + "_licenseName"));
            lic.setLicenseWebsite(getStringResourceByName(ctx, "license_" + licenseName + "_licenseWebsite"));
            lic.setLicenseShortDescription(getStringResourceByName(ctx, "license_" + licenseName + "_licenseShortDescription"));
            lic.setLicenseDescription(getStringResourceByName(ctx, "license_" + licenseName + "_licenseDescription"));
            return lic;
        } catch (Exception ex) {
            Log.e("aboutlibraries", "Failed to generateLicense from file: " + ex.toString());
            return null;
        }
    }

    /**
     * @param libraryName
     * @return
     */
    private Library genLibrary(Context ctx, String libraryName) {
        libraryName = libraryName.replace("-", "_");

        try {
            Library lib = new Library();

            //Get custom vars to insert into defined areas
            HashMap<String, String> customVariables = getCustomVariables(ctx, libraryName);

            lib.setDefinedName(libraryName);
            lib.setAuthor(getStringResourceByName(ctx, "library_" + libraryName + "_author"));
            lib.setAuthorWebsite(getStringResourceByName(ctx, "library_" + libraryName + "_authorWebsite"));
            lib.setLibraryName(getStringResourceByName(ctx, "library_" + libraryName + "_libraryName"));
            lib.setLibraryDescription(insertVariables(getStringResourceByName(ctx, "library_" + libraryName + "_libraryDescription"), customVariables));
            lib.setLibraryVersion(getStringResourceByName(ctx, "library_" + libraryName + "_libraryVersion"));
            lib.setLibraryWebsite(getStringResourceByName(ctx, "library_" + libraryName + "_libraryWebsite"));

            String licenseId = getStringResourceByName(ctx, "library_" + libraryName + "_licenseId");
            if (TextUtils.isEmpty(licenseId)) {
                License license = new License();
                license.setLicenseName(getStringResourceByName(ctx, "library_" + libraryName + "_licenseVersion"));
                license.setLicenseWebsite(getStringResourceByName(ctx, "library_" + libraryName + "_licenseLink"));
                license.setLicenseShortDescription(insertVariables(getStringResourceByName(ctx, "library_" + libraryName + "_licenseContent"), customVariables));
                lib.setLicense(license);
            } else {
                License license = getLicense(licenseId);
                if (license != null) {
                    license = license.copy();
                    license.setLicenseShortDescription(insertVariables(license.getLicenseShortDescription(), customVariables));
                    license.setLicenseDescription(insertVariables(license.getLicenseDescription(), customVariables));
                    lib.setLicense(license);
                }
            }

            lib.setOpenSource(Boolean.valueOf(getStringResourceByName(ctx, "library_" + libraryName + "_isOpenSource")));
            lib.setRepositoryLink(getStringResourceByName(ctx, "library_" + libraryName + "_repositoryLink"));

            lib.setClassPath(getStringResourceByName(ctx, "library_" + libraryName + "_classPath"));

            if (TextUtils.isEmpty(lib.getLibraryName()) && TextUtils.isEmpty(lib.getLibraryDescription())) {
                return null;
            }

            return lib;
        } catch (Exception ex) {
            Log.e("aboutlibraries", "Failed to generateLibrary from file: " + ex.toString());
            return null;
        }
    }

    /**
     * @param libraryName
     * @return
     */
    public HashMap<String, String> getCustomVariables(Context ctx, String libraryName) {
        HashMap<String, String> customVariables = new HashMap<String, String>();

        String customVariablesString = getStringResourceByName(ctx, DEFINE_EXT + libraryName);
        if (TextUtils.isEmpty(customVariablesString)) {
            customVariablesString = getStringResourceByName(ctx, DEFINE_INT + libraryName);
        }

        if (!TextUtils.isEmpty(customVariablesString)) {
            String[] customVariableArray = customVariablesString.split(";");
            if (customVariableArray.length > 0) {
                for (String customVariableKey : customVariableArray) {
                    String customVariableContent = getStringResourceByName(ctx, "library_" + libraryName + "_" + customVariableKey);
                    if (!TextUtils.isEmpty(customVariableContent)) {
                        customVariables.put(customVariableKey, customVariableContent);
                    }
                }
            }
        }

        return customVariables;
    }

    public String insertVariables(String insertInto, HashMap<String, String> variables) {
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            if (!TextUtils.isEmpty(entry.getValue())) {
                insertInto = insertInto.replace("<<<" + entry.getKey().toUpperCase() + ">>>", entry.getValue());
            }
        }

        //remove the placeholder chars so the license is shown correct
        insertInto = insertInto.replace("<<<", "");
        insertInto = insertInto.replace(">>>", "");

        return insertInto;
    }

    public String getStringResourceByName(Context ctx, String aString) {
        String packageName = ctx.getPackageName();

        int resId = ctx.getResources().getIdentifier(aString, "string", packageName);
        if (resId == 0) {
            return "";
        } else {
            return ctx.getString(resId);
        }
    }


    /**
     * @param modifications
     */
    public void modifyLibraries(HashMap<String, HashMap<String, String>> modifications) {
        if (modifications != null) {
            for (Map.Entry<String, HashMap<String, String>> entry : modifications.entrySet()) {
                ArrayList<Library> foundLibs = findInExternalLibrary(entry.getKey(), true, 1);
                if (foundLibs == null || foundLibs.size() == 0) {
                    foundLibs = findInInternalLibrary(entry.getKey(), true, 1);
                }

                if (foundLibs != null && foundLibs.size() == 1) {
                    Library lib = foundLibs.get(0);
                    for (Map.Entry<String, String> modification : entry.getValue().entrySet()) {
                        String key = modification.getKey().toUpperCase();
                        String value = modification.getValue();

                        if (key.equals(LibraryFields.AUTHOR_NAME.name())) {
                            lib.setAuthor(value);
                        } else if (key.equals(LibraryFields.AUTHOR_WEBSITE.name())) {
                            lib.setAuthorWebsite(value);
                        } else if (key.equals(LibraryFields.LIBRARY_NAME.name())) {
                            lib.setLibraryName(value);
                        } else if (key.equals(LibraryFields.LIBRARY_DESCRIPTION.name())) {
                            lib.setLibraryDescription(value);
                        } else if (key.equals(LibraryFields.LIBRARY_VERSION.name())) {
                            lib.setLibraryVersion(value);
                        } else if (key.equals(LibraryFields.LIBRARY_WEBSITE.name())) {
                            lib.setLibraryWebsite(value);
                        } else if (key.equals(LibraryFields.LIBRARY_OPEN_SOURCE.name())) {
                            lib.setOpenSource(Boolean.parseBoolean(value));
                        } else if (key.equals(LibraryFields.LIBRARY_REPOSITORY_LINK.name())) {
                            lib.setRepositoryLink(value);
                        } else if (key.equals(LibraryFields.LIBRARY_CLASSPATH.name())) {
                            //note this can be set but won't probably work for autodetect
                            lib.setClassPath(value);
                        } else if (key.equals(LibraryFields.LICENSE_NAME.name())) {
                            if (lib.getLicense() == null) {
                                lib.setLicense(new License());
                            }
                            lib.getLicense().setLicenseName(value);
                        } else if (key.equals(LibraryFields.LICENSE_SHORT_DESCRIPTION.name())) {
                            if (lib.getLicense() == null) {
                                lib.setLicense(new License());
                            }
                            lib.getLicense().setLicenseShortDescription(value);
                        } else if (key.equals(LibraryFields.LICENSE_DESCRIPTION.name())) {
                            if (lib.getLicense() == null) {
                                lib.setLicense(new License());
                            }
                            lib.getLicense().setLicenseDescription(value);
                        } else if (key.equals(LibraryFields.LICENSE_WEBSITE.name())) {
                            if (lib.getLicense() == null) {
                                lib.setLicense(new License());
                            }
                            lib.getLicense().setLicenseWebsite(value);
                        }
                    }
                }
            }
        }
    }
}