//
//  TestCategory.hpp
//  Soar-xcode
//
//  Created by Alex Turner on 6/16/15.
//  Copyright © 2015 University of Michigan – Soar Group. All rights reserved.
//

#ifndef TestCategory_cpp
#define TestCategory_cpp

#include <cstdint>

#include <vector>
#include <functional>
#include <map>
#include <string>

#include <iostream>

#include "TestHelpers.hpp"

class TestRunner;

class TestCategory
{
public:
	typedef std::tuple<TestFunction*, uint64_t, std::string> TestCategory_test;

private:
	TestCategory();
	
	std::string m_categoryName;
	
public:
	std::vector<TestCategory_test> m_TestCategory_tests;

#pragma mark - Getters & Setters	
	const std::string getCategoryName() { return m_categoryName; }
	const std::vector<TestCategory_test> getTests() { return m_TestCategory_tests; }
	
	TestRunner* runner;
	
#pragma mark - Test Helpers
	
	class Test
	{
	public:
		Test(std::string testName, TestFunction* testFunction, uint64_t timeoutMs, std::vector<TestCategory_test>& tests)
		{
			tests.push_back(std::make_tuple(testFunction, timeoutMs, testName));
		}
	};
	
	TestCategory(std::string testCategoryName) : m_categoryName(testCategoryName) {}
	
#pragma mark - Test Functions
	
	virtual void before() {};
	virtual void after() {};
};

#endif /* TestCategory_cpp */
